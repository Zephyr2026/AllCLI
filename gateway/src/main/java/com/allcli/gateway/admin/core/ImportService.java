package com.allcli.gateway.admin.core;

import com.allcli.gateway.admin.api.dto.ImportRequest;
import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.core.GatewayUtil;
import com.allcli.gateway.model.System;
import com.allcli.gateway.persistence.SystemRepository;
import com.allcli.gateway.persistence.ToolDefinitionRepository;
import com.allcli.engine.mapper.ToolMapper;
import com.allcli.engine.model.ConvertRequest;
import com.allcli.engine.parser.OpenApiLoader;
import io.swagger.v3.oas.models.OpenAPI;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 导入编排（FR-CONV-01/02/03，admin 面）：同步前置校验 → 异步转化落库。
 *
 * <p>复用 engine 纯 Java 转化（OpenApiLoader/ToolMapper），落库冲突策略比 CLI 的
 * upsert 更保守：同 toolId 已存在即记 conflicts，不覆盖（避免重导入悄悄改掉 published
 * 工具）。draft 不进 MCP、不发目录事件。
 */
@Service
public class ImportService {

    /** sourceUrl 抓取大小上限（防大响应打爆内存）。 */
    private static final long MAX_CONTENT_BYTES = 5L * 1024 * 1024;

    private final ImportJobStore jobStore;
    private final ImportExecutor executor;
    private final SystemRepository systemRepository;
    private final ToolDefinitionRepository toolRepo;
    private final AdminToolMapper toolMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public ImportService(ImportJobStore jobStore, ImportExecutor executor,
                         SystemRepository systemRepository,
                         ToolDefinitionRepository toolRepo, AdminToolMapper toolMapper) {
        this.jobStore = jobStore;
        this.executor = executor;
        this.systemRepository = systemRepository;
        this.toolRepo = toolRepo;
        this.toolMapper = toolMapper;
    }

    /** 提交：同步前置校验（失败即抛，不产生 job）→ 返回 running job。 */
    public com.allcli.gateway.admin.core.ImportJob submit(ImportRequest req) {
        String systemRef = requireText(req.getSystemRef(), "systemRef");
        boolean hasContent = req.getSourceContent() != null && !req.getSourceContent().isBlank();
        boolean hasUrl = req.getSourceUrl() != null && !req.getSourceUrl().isBlank();
        if (hasContent == hasUrl) {
            throw GatewayException.of("VALIDATION_ERROR", "validation",
                    "sourceUrl 与 sourceContent 必须二选一", false);
        }
        if (hasUrl) {
            String url = req.getSourceUrl().trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw GatewayException.of("VALIDATION_ERROR", "validation",
                        "sourceUrl 仅支持 http/https", false);
            }
        }
        Pattern exclude = null;
        if (req.getExcludeRegex() != null && !req.getExcludeRegex().isBlank()) {
            try {
                exclude = Pattern.compile(req.getExcludeRegex());
            } catch (Exception e) {
                throw GatewayException.of("VALIDATION_ERROR", "validation",
                        "excludeRegex 无法编译: " + e.getMessage(), false);
            }
        }
        System system = systemRepository.findBySystemId(systemRef)
                .orElseThrow(() -> GatewayException.of("SYSTEM_NOT_FOUND", "business",
                        "系统未注册，请先 POST /api/v1/systems: " + systemRef, false));

        ImportJob job = jobStore.create(GatewayUtil.id("job"), systemRef);
        final String owner = (req.getOwner() == null || req.getOwner().isBlank())
                ? system.getOwner() : req.getOwner().trim();
        final Pattern excludePattern = exclude;
        executor.submit(() -> run(job, req, owner, excludePattern));
        return job;
    }

    public com.allcli.gateway.admin.core.ImportJob status(String jobId) {
        com.allcli.gateway.admin.core.ImportJob job = jobStore.get(jobId);
        if (job == null) {
            throw GatewayException.of("IMPORT_JOB_NOT_FOUND", "validation",
                    "导入任务不存在（或已随重启丢失）: " + jobId, false);
        }
        return job;
    }

    private void run(ImportJob job, ImportRequest req, String owner, Pattern exclude) {
        try {
            String content = req.getSourceContent() != null && !req.getSourceContent().isBlank()
                    ? req.getSourceContent()
                    : fetch(req.getSourceUrl().trim());
            OpenAPI openAPI = OpenApiLoader.parse(content);
            if (openAPI == null || openAPI.getPaths() == null) {
                throw GatewayException.of("IMPORT_PARSE_FAILED", "upstream",
                        "OpenAPI 文档无法解析（缺少 paths）", true);
            }
            ConvertRequest convertReq = ConvertRequest.builder()
                    .input(req.getSourceUrl() != null ? req.getSourceUrl() : "inline-content")
                    .systemRef(job.getSystemRef())
                    .credentialRef(req.getCredentialRef())
                    .owner(owner)
                    .version(req.getVersion() == null || req.getVersion().isBlank() ? "1.0.0" : req.getVersion())
                    .timeoutMs(req.getTimeoutMs() == null ? 30000L : req.getTimeoutMs())
                    .build();
            List<com.allcli.engine.model.ToolDefinition> tools =
                    new ToolMapper(openAPI).map(openAPI, convertReq).getTools();

            List<String> created = new ArrayList<>();
            List<String> conflicts = new ArrayList<>();
            for (com.allcli.engine.model.ToolDefinition src : filter(tools, req.getIncludePaths(), exclude)) {
                if (toolRepo.findByToolId(src.getToolId()).isPresent()) {
                    conflicts.add(src.getToolId());
                    continue;
                }
                try {
                    toolRepo.save(toolMapper.toGatewayTool(src));
                    created.add(src.getToolId());
                } catch (DataIntegrityViolationException e) {
                    // 并发重导撞 uk_tool_id
                    conflicts.add(src.getToolId());
                }
            }
            job.setCreated(created);
            job.setConflicts(conflicts);
            job.setStatus("done");
        } catch (GatewayException e) {
            fail(job, e.getCode(), e.getMessage());
        } catch (Exception e) {
            fail(job, "IMPORT_FAILED", String.valueOf(e.getMessage()));
        } finally {
            job.setFinishedAt(java.time.LocalDateTime.now());
        }
    }

    private String fetch(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<InputStream> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw GatewayException.of("IMPORT_FETCH_FAILED", "upstream",
                    "抓取 sourceUrl 失败: HTTP " + resp.statusCode(), true);
        }
        try (InputStream in = resp.body()) {
            byte[] buffer = new byte[8192];
            int n;
            long total = 0;
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > MAX_CONTENT_BYTES) {
                    throw GatewayException.of("IMPORT_TOO_LARGE", "validation",
                            "sourceUrl 内容超过 5MB 上限", false);
                }
                out.write(buffer, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    /** includePaths 前缀过滤 + excludeRegex 剔除（作用于转化结果，不改 ToolMapper）。 */
    private List<com.allcli.engine.model.ToolDefinition> filter(
            List<com.allcli.engine.model.ToolDefinition> tools,
            List<String> includePaths, Pattern exclude) {
        List<String> prefixes = includePaths == null ? List.of()
                : includePaths.stream().filter(p -> p != null && !p.isBlank()).map(String::trim).toList();
        List<com.allcli.engine.model.ToolDefinition> out = new ArrayList<>();
        for (com.allcli.engine.model.ToolDefinition t : tools) {
            Object path = t.getEndpoint() == null ? null : t.getEndpoint().get("path");
            String p = path == null ? "" : String.valueOf(path);
            if (!prefixes.isEmpty() && prefixes.stream().noneMatch(p::startsWith)) {
                continue;
            }
            if (exclude != null && exclude.matcher(p).matches()) {
                continue;
            }
            out.add(t);
        }
        return out;
    }

    private void fail(ImportJob job, String code, String message) {
        job.setStatus("failed");
        job.setErrorCode(code);
        job.setErrorMessage(message);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw GatewayException.of("VALIDATION_ERROR", "validation", field + " 不能为空", false);
        }
        return value.trim();
    }
}
