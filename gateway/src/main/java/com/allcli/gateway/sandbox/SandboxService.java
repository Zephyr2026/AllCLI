package com.allcli.gateway.sandbox;

import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.sandbox.dto.QualityReport;
import com.allcli.gateway.sandbox.dto.QualityReport.QualityItem;
import com.allcli.gateway.sandbox.dto.SandboxResponse;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.jackson2.DefaultJsonSchemaValidator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 沙箱试运行（FR-AGENT-03）+ 语义质量自检（FR-SEM-05）。
 *
 * <p>沙箱：入参校验（复用 MCP SDK 校验器）后由 outputSchema 生成 mock 响应——
 * 不触达真实系统、不解密真实凭证、跳过限流/幂等/HITL、不写调用审计。
 * 专用于 draft 工具上线前自检与回归（无订阅也可试）。
 *
 * <p>质量自检为 advisory：一期发布门禁仅强制 write 工具标注 reversibility（M3 现状），
 * 其余未通过项以 warn/fail 提示，不拦截发布。
 */
@Service
public class SandboxService {

    private final JsonSchemaValidator validator = new DefaultJsonSchemaValidator();

    private static final List<String> SANDBOX_NOTES = List.of(
            "mock 凭证：未解密、未注入真实凭证",
            "未触达真实系统（不调用下游 endpoint）",
            "跳过限流/幂等/HITL 分级",
            "不产生调用审计（sys_invocation）");

    /** 沙箱试运行。入参缺省时回退 examples[0]，再回退 inputSchema 生成。 */
    public SandboxResponse run(ToolDefinition tool, Map<String, Object> arguments) {
        Map<String, Object> args = arguments;
        String argSource = "request";
        if (args == null) {
            Map<String, Object> fromExample = firstExampleArgs(tool);
            if (fromExample != null) {
                args = fromExample;
                argSource = "example";
            } else {
                args = MockValueGenerator.sampleArguments(tool.getInputSchema());
                argSource = "generated";
            }
        }
        validate(tool, args);

        Object result = MockValueGenerator.outputOf(tool.getOutputSchema());
        if (result == null) {
            result = Map.of("message", "sandbox mock response（无 outputSchema 或无响应体）");
        }
        return SandboxResponse.builder()
                .toolId(tool.getToolId())
                .version(tool.getVersion())
                .sideEffect(tool.getSideEffect())
                .status("succeeded")
                .sandbox(true)
                .argSource(argSource)
                .arguments(args)
                .result(result)
                .notes(SANDBOX_NOTES)
                .build();
    }

    /** FR-SEM-05 checklist：description / inputSchema / examples 沙箱可达 / 写操作防护。 */
    public QualityReport quality(ToolDefinition tool) {
        List<QualityItem> items = new ArrayList<>();

        String desc = tool.getDescription();
        boolean descOk = desc != null && desc.trim().length() >= 6;
        items.add(QualityItem.builder()
                .item("description")
                .status(descOk ? "pass" : "fail")
                .detail(descOk ? "描述非空且达最小长度" : "description 为空或过短（<6 字符）；动词+对象语义检测留二期")
                .build());

        Map<String, Object> schema = tool.getInputSchema();
        boolean schemaOk = schema != null && "object".equals(schema.get("type"));
        items.add(QualityItem.builder()
                .item("inputSchema")
                .status(schemaOk ? "pass" : "fail")
                .detail(schemaOk ? "JSON Schema 合法（type=object）"
                        : "inputSchema 缺失或 type≠object，Agent 无法可靠构造入参")
                .build());

        items.add(exampleItem(tool));

        items.add(writeGuardItem(tool));

        boolean anyFail = items.stream().anyMatch(i -> "fail".equals(i.getStatus()));
        boolean anyWarn = items.stream().anyMatch(i -> "warn".equals(i.getStatus()));
        return QualityReport.builder()
                .toolId(tool.getToolId())
                .overall(anyFail ? "fail" : anyWarn ? "warn" : "pass")
                .items(items)
                .build();
    }

    private QualityItem exampleItem(ToolDefinition tool) {
        if (tool.getExamples() == null || tool.getExamples().isEmpty()) {
            return QualityItem.builder()
                    .item("examples")
                    .status("warn")
                    .detail("无示例；当前转化引擎未产出 examples，建议人工补录（一期发布不强制）")
                    .build();
        }
        try {
            run(tool, null);
            return QualityItem.builder()
                    .item("examples")
                    .status("pass")
                    .detail("examples[0] 沙箱试调成功")
                    .build();
        } catch (GatewayException e) {
            return QualityItem.builder()
                    .item("examples")
                    .status("fail")
                    .detail("examples[0] 沙箱试调失败: " + e.getCode() + " " + e.getMessage())
                    .build();
        }
    }

    private QualityItem writeGuardItem(ToolDefinition tool) {
        if (!"write".equals(tool.getSideEffect())) {
            return QualityItem.builder()
                    .item("writeGuard")
                    .status("pass")
                    .detail("只读工具无需写操作防护")
                    .build();
        }
        List<String> problems = new ArrayList<>();
        boolean reversibilityOk = "reversible".equals(tool.getReversibility())
                || "irreversible".equals(tool.getReversibility());
        if (!reversibilityOk) {
            problems.add("reversibility 未标注（发布门禁拦截）");
        }
        if (tool.getIdempotencyKey() == null || tool.getIdempotencyKey().isEmpty()) {
            problems.add("idempotencyKey 未声明（一期发布不强制）");
        }
        boolean hitlOn = tool.getHitl() != null && Boolean.TRUE.equals(tool.getHitl().get("enabled"));
        if (!hitlOn) {
            problems.add("HITL 未启用（写操作默认应 HITL）");
        }
        if (problems.isEmpty()) {
            return QualityItem.builder()
                    .item("writeGuard")
                    .status("pass")
                    .detail("write 工具已配 reversibility + idempotencyKey + HITL")
                    .build();
        }
        boolean blocking = !reversibilityOk;
        return QualityItem.builder()
                .item("writeGuard")
                .status(blocking ? "fail" : "warn")
                .detail(String.join("；", problems))
                .build();
    }

    /** examples 条目兼容两种形态：{"arguments":{...}} 或入参对象本身。 */
    private Map<String, Object> firstExampleArgs(ToolDefinition tool) {
        if (tool.getExamples() == null || tool.getExamples().isEmpty()) {
            return null;
        }
        Map<String, Object> ex = tool.getExamples().get(0);
        if (ex == null) {
            return null;
        }
        Object nested = ex.get("arguments");
        if (nested instanceof Map<?, ?> m) {
            Map<String, Object> args = new LinkedHashMap<>();
            m.forEach((k, v) -> args.put(String.valueOf(k), v));
            return args;
        }
        return ex;
    }

    private void validate(ToolDefinition tool, Map<String, Object> args) {
        Map<String, Object> schema = tool.getInputSchema();
        if (schema == null || schema.isEmpty()) {
            return;
        }
        try {
            JsonSchemaValidator.ValidationResponse resp =
                    validator.validate(schema, args == null ? Map.of() : args);
            if (!resp.valid()) {
                throw GatewayException.of("INTENT_REJECTED", "validation",
                        "沙箱入参校验失败: " + resp.errorMessage(), false);
            }
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw GatewayException.of("INTENT_REJECTED", "validation",
                    "沙箱入参校验异常: " + e.getMessage(), false);
        }
    }
}
