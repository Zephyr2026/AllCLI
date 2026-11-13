package com.allcli.gateway.sandbox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 确定性 mock 值生成器：按 JSON Schema 递归产出示例值（FR-AGENT-03 沙箱）。
 *
 * <p>值固定不随机（同 schema 恒同输出），便于回归对比与测试断言。
 * 转化引擎产出的 outputSchema 以响应码为键（如 "200"→schema），先解包再生成。
 */
public final class MockValueGenerator {

    /** 递归深度上限，防 $ref 自引用死循环。 */
    private static final int MAX_DEPTH = 5;

    private MockValueGenerator() {
    }

    /**
     * 由 outputSchema 生成 mock 响应。
     * 顶层为响应码键（"200"/"201"...）时取最小 2xx；无 schema 或空响应体
     * （如 204）返回 null，由调用方兜底。
     */
    public static Object outputOf(Map<String, Object> outputSchema) {
        if (outputSchema == null || outputSchema.isEmpty()) {
            return null;
        }
        Map<String, Object> schema = unwrapResponseCode(outputSchema);
        if (schema == null || schema.isEmpty()) {
            return null;
        }
        return valueOf(schema, null, 0);
    }

    /** 由 inputSchema（type=object）生成示例入参：含全部 properties（required 与否都给，便于人工删减）。 */
    public static Map<String, Object> sampleArguments(Map<String, Object> inputSchema) {
        if (inputSchema == null || inputSchema.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Object v = valueOf(inputSchema, "arguments", 0);
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> args = new LinkedHashMap<>();
            m.forEach((k, val) -> args.put(String.valueOf(k), val));
            return args;
        }
        return new LinkedHashMap<>();
    }

    /** 顶层键若全为数字（响应码），取最小的 2xx；否则原样返回。 */
    private static Map<String, Object> unwrapResponseCode(Map<String, Object> schema) {
        boolean allNumeric = !schema.isEmpty()
                && schema.keySet().stream().allMatch(k -> k.matches("\\d{3}"));
        if (!allNumeric) {
            return schema;
        }
        return schema.entrySet().stream()
                .filter(e -> {
                    int code = Integer.parseInt(e.getKey());
                    return code >= 200 && code < 300;
                })
                .min(Map.Entry.comparingByKey())
                .or(() -> schema.entrySet().stream().min(Map.Entry.comparingByKey()))
                .map(e -> asMap(e.getValue()))
                .orElse(schema);
    }

    private static Object valueOf(Map<String, Object> schema, String name, int depth) {
        if (schema == null || schema.isEmpty()) {
            return "mock_" + safeName(name);
        }
        if (depth >= MAX_DEPTH) {
            return "mock_" + safeName(name);
        }
        Object enumVal = schema.get("enum");
        if (enumVal instanceof List<?> values && !values.isEmpty()) {
            return values.get(0);
        }
        Object def = schema.get("default");
        if (def != null) {
            return def;
        }
        String type = schema.get("type") == null ? null : String.valueOf(schema.get("type"));
        if (type == null) {
            // 无 type：按结构推断
            if (schema.containsKey("properties")) {
                type = "object";
            } else if (schema.containsKey("items")) {
                type = "array";
            }
        }
        if (type == null) {
            return "mock_" + safeName(name);
        }
        switch (type) {
            case "object" -> {
                Map<String, Object> obj = new LinkedHashMap<>();
                Map<String, Object> props = asMap(schema.get("properties"));
                props.forEach((k, v) -> obj.put(k, valueOf(asMap(v), k, depth + 1)));
                return obj;
            }
            case "array" -> {
                List<Object> arr = new ArrayList<>();
                Map<String, Object> items = asMap(schema.get("items"));
                arr.add(valueOf(items, name != null ? name + "_item" : "item", depth + 1));
                arr.add(valueOf(items, name != null ? name + "_item" : "item", depth + 1));
                return arr;
            }
            case "string" -> {
                return mockString(schema, name);
            }
            case "integer" -> {
                return 42;
            }
            case "number" -> {
                return 3.14;
            }
            case "boolean" -> {
                return true;
            }
            default -> {
                return "mock_" + safeName(name);
            }
        }
    }

    private static String mockString(Map<String, Object> schema, String name) {
        String format = schema.get("format") == null ? "" : String.valueOf(schema.get("format"));
        return switch (format) {
            case "date" -> "2026-01-01";
            case "date-time" -> "2026-01-01T00:00:00Z";
            case "email" -> "mock@example.com";
            case "uri", "url" -> "https://example.com";
            case "uuid" -> "00000000-0000-0000-0000-000000000000";
            default -> "mock_" + safeName(name);
        };
    }

    private static String safeName(String name) {
        return name == null || name.isEmpty() ? "value" : name;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, val) -> out.put(String.valueOf(k), val));
            return out;
        }
        return new LinkedHashMap<>();
    }
}
