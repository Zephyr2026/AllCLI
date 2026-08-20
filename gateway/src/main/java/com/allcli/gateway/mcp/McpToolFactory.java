package com.allcli.gateway.mcp;

import com.allcli.gateway.model.ToolDefinition;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * ToolDefinition → MCP Tool 映射（PRD §11.1）：
 * name=toolId、inputSchema 直传；annotations 映射治理提示（readOnly/destructive/idempotent）；
 * 治理全量字段经规范扩展通道 _meta 输出（sideEffect/reversibility/HITL 等）。
 */
@Component
public class McpToolFactory {

    public McpSchema.Tool toTool(ToolDefinition t) {
        Map<String, Object> in = t.getInputSchema() == null ? Map.of() : t.getInputSchema();
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                str(in.get("type"), "object"),
                strMap(in.get("properties")),
                strList(in.get("required")),
                null, null, null);

        boolean readonly = "readonly".equals(t.getSideEffect());
        McpSchema.ToolAnnotations annotations = new McpSchema.ToolAnnotations(
                t.getName(),
                readonly,
                "irreversible".equals(t.getReversibility()),
                readonly || t.getIdempotencyKey() != null,
                true,
                null);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("sideEffect", t.getSideEffect());
        meta.put("reversibility", t.getReversibility());
        if (t.getIdempotencyKey() != null) {
            meta.put("idempotencyKey", t.getIdempotencyKey());
        }
        if (t.getRequiredPermission() != null && !t.getRequiredPermission().isEmpty()) {
            meta.put("requiredPermission", t.getRequiredPermission());
        }
        if (t.getHitl() != null) {
            meta.put("hitl", t.getHitl());
        }
        meta.put("version", t.getVersion());
        meta.put("system", t.getSystemRef());

        String description = t.getDescription() == null || t.getDescription().isBlank()
                ? t.getName() : t.getDescription();

        return McpSchema.Tool.builder()
                .name(t.getToolId())
                .title(t.getName())
                .description(description)
                .inputSchema(inputSchema)
                .annotations(annotations)
                .meta(meta)
                .build();
    }

    private static String str(Object o, String dft) {
        return o instanceof String s ? s : dft;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> strMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static List<String> strList(Object o) {
        return o instanceof List<?> l
                ? l.stream().map(String::valueOf).toList()
                : List.of();
    }
}
