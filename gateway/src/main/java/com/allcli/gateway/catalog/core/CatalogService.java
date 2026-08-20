package com.allcli.gateway.catalog.core;

import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.catalog.api.dto.PublishRequest;
import com.allcli.gateway.catalog.api.dto.PublishResponse;
import com.allcli.gateway.catalog.api.dto.ToolDetailResponse;
import com.allcli.gateway.catalog.api.dto.ToolSummaryResponse;
import com.allcli.gateway.catalog.event.ToolCatalogChangedEvent;
import com.allcli.gateway.core.GatewayUtil;
import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.model.ToolVersion;
import com.allcli.gateway.persistence.ToolDefinitionRepository;
import com.allcli.gateway.persistence.ToolVersionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 目录服务：检索/详情（FR-REG-02）+ 发布/下线（FR-REG-01，draft→published/gray→deprecated）。
 *
 * <p>发布写 sys_tool_version 快照；write 工具未正确标注 reversibility 禁止发布
 * （MISSING_REVERSIBILITY，PRD §13.1）。变更经事务提交后发 ToolCatalogChangedEvent。
 */
@Service
public class CatalogService {

    private static final Set<String> VALID_TARGETS = Set.of("published", "gray");

    private final ToolDefinitionRepository toolRepo;
    private final ToolVersionRepository versionRepo;
    private final ApplicationEventPublisher eventPublisher;

    public CatalogService(ToolDefinitionRepository toolRepo, ToolVersionRepository versionRepo,
                          ApplicationEventPublisher eventPublisher) {
        this.toolRepo = toolRepo;
        this.versionRepo = versionRepo;
        this.eventPublisher = eventPublisher;
    }

    public Page<ToolSummaryResponse> list(String q, String system, String reversibility,
                                          String status, Pageable pageable) {
        return toolRepo
                .findAll(ToolSpecifications.withFilters(q, system, reversibility, status), pageable)
                .map(ToolSanitizer::toSummary);
    }

    public ToolDetailResponse detail(String toolId) {
        return toolRepo.findByToolId(toolId)
                .map(ToolSanitizer::toDetail)
                .orElseThrow(() -> GatewayException.of("TOOL_NOT_FOUND", "validation",
                        "工具不存在: " + toolId, false));
    }

    /** 供 publish/deprecate 等写路径复用。 */
    public ToolDefinition requireTool(String toolId) {
        return toolRepo.findByToolId(toolId)
                .orElseThrow(() -> GatewayException.of("TOOL_NOT_FOUND", "validation",
                        "工具不存在: " + toolId, false));
    }

    /** 发布：校验状态机与门禁 → 写版本快照 → 状态流转 → 提交后发事件。 */
    @Transactional
    public PublishResponse publish(String toolId, PublishRequest req) {
        ToolDefinition tool = requireTool(toolId);
        if ("deprecated".equals(tool.getStatus())) {
            throw GatewayException.of("INVALID_STATUS_TRANSITION", "business",
                    "已下线工具不可发布: " + toolId, false);
        }
        // 门禁：write 工具必须标注 reversibility（reversible/irreversible），readonly 视为未标注
        if ("write".equals(tool.getSideEffect())
                && !Set.of("reversible", "irreversible").contains(tool.getReversibility())) {
            throw GatewayException.of("MISSING_REVERSIBILITY", "validation",
                    "write 工具未标注 reversibility，禁止发布: " + toolId, false);
        }
        String target = req == null || req.getTarget() == null ? "published" : req.getTarget().trim();
        if (!VALID_TARGETS.contains(target)) {
            throw GatewayException.of("VALIDATION_ERROR", "validation",
                    "target 仅支持 published/gray: " + target, false);
        }
        if ("gray".equals(target)) {
            Integer pct = req.getGrayPercent();
            if (pct == null || pct < 1 || pct > 100) {
                throw GatewayException.of("VALIDATION_ERROR", "validation",
                        "target=gray 时 grayPercent 必填且为 1..100", false);
            }
        }

        // 版本快照（同版本只允许发布一次）
        String version = tool.getVersion();
        if (versionRepo.existsByToolIdAndVersion(toolId, version)) {
            throw GatewayException.of("TOOL_VERSION_EXISTS", "business",
                    "版本快照已存在，先升版本再发布: " + toolId + "@" + version, false);
        }
        tool.setStatus(target);
        Map<String, Object> snapshot = snapshotOf(tool, req);
        ToolVersion sv = versionRepo.save(ToolVersion.builder()
                .toolId(toolId)
                .version(version)
                .changelog(req == null ? null : req.getChangelog())
                .definitionSnapshot(snapshot)
                .createdAt(LocalDateTime.now())
                .build());
        toolRepo.save(tool);

        eventPublisher.publishEvent(new ToolCatalogChangedEvent(
                toolId, "gray".equals(target)
                        ? ToolCatalogChangedEvent.ACTION_GRAY
                        : ToolCatalogChangedEvent.ACTION_PUBLISHED,
                version));
        return PublishResponse.builder()
                .toolId(toolId)
                .version(version)
                .status(target)
                .snapshotId(sv.getId())
                .build();
    }

    /** 下线：任何非 deprecated 状态可下线；不产生新快照。 */
    @Transactional
    public PublishResponse deprecate(String toolId) {
        ToolDefinition tool = requireTool(toolId);
        if ("deprecated".equals(tool.getStatus())) {
            throw GatewayException.of("INVALID_STATUS_TRANSITION", "business",
                    "工具已是下线状态: " + toolId, false);
        }
        tool.setStatus("deprecated");
        toolRepo.save(tool);
        eventPublisher.publishEvent(new ToolCatalogChangedEvent(
                toolId, ToolCatalogChangedEvent.ACTION_DEPRECATED, tool.getVersion()));
        return PublishResponse.builder()
                .toolId(toolId)
                .version(tool.getVersion())
                .status("deprecated")
                .build();
    }

    /** 发布快照：ToolDefinition 全字段（剔除自增 id），附发布元数据。 */
    private Map<String, Object> snapshotOf(ToolDefinition tool, PublishRequest req) {
        Map<String, Object> snap = GatewayUtil.SORTED_MAPPER.convertValue(
                tool, new TypeReference<Map<String, Object>>() {
                });
        snap.remove("id");
        if (req != null && req.getGrayPercent() != null) {
            snap.put("grayPercent", req.getGrayPercent());
        }
        return snap;
    }
}
