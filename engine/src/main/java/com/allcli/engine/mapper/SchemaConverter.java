package com.allcli.engine.mapper;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 递归把 swagger-core {@link Schema} 转为 JSON-Schema 风格的 {@code Map<String,Object>}。
 *
 * <p>纯函数、无 Spring 依赖。对标原 Go 版 {@code schemaToMap}。
 *
 * <p>输出字段（按原 Go 规约，按需出现）：
 * <ul>
 *   <li>{@code type} —— 缺省时回退 {@code "string"}（自实现，因 {@code inferSchemaType=false}）。</li>
 *   <li>{@code properties} —— object 类型时，每个属性递归转换。</li>
 *   <li>{@code items} —— array 类型时，递归转换。</li>
 *   <li>{@code description} / {@code enum} —— 有才输出。</li>
 *   <li>{@code required} —— object 类型时，若有则输出（用户决定带上，语义更完整）。</li>
 * </ul>
 */
public final class SchemaConverter {

    private final OpenAPI openAPI;

    public SchemaConverter(OpenAPI openAPI) {
        this.openAPI = openAPI;
    }

    /**
     * 把一个 Schema 转为 Map。schema 为 null 时返回空 Map（如无响应体）。
     */
    public Map<String, Object> toMap(Schema<?> schema) {
        if (schema == null) {
            return new LinkedHashMap<>();
        }
        // 防御性 $ref 兜底：resolveFully 对 allOf/callback 内的嵌套 $ref 偶有残留
        schema = resolveRef(schema);

        Map<String, Object> map = new LinkedHashMap<>();
        String type = schema.getType();
        // 缺省 type 回退 string（对标 Go 规约第 7 条；因 inferSchemaType=false 由这里兜底）
        map.put("type", type != null ? type : "string");

        if (schema.getDescription() != null && !schema.getDescription().isEmpty()) {
            map.put("description", schema.getDescription());
        }

        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            map.put("enum", schema.getEnum());
        }

        // object：递归 properties + required
        Map<String, Schema> properties = schema.getProperties();
        if (properties != null && !properties.isEmpty()) {
            Map<String, Object> propsMap = new LinkedHashMap<>();
            for (Map.Entry<String, Schema> e : properties.entrySet()) {
                propsMap.put(e.getKey(), toMap(e.getValue()));
            }
            map.put("properties", propsMap);

            List<String> required = schema.getRequired();
            if (required != null && !required.isEmpty()) {
                map.put("required", required);
            }
        }

        // array：递归 items
        if (schema.getItems() != null) {
            map.put("items", toMap(schema.getItems()));
        }

        return map;
    }

    /**
     * 若 schema 仍带 $ref（resolveFully 边界），查 components 手动解析；否则原样返回。
     */
    @SuppressWarnings("unchecked")
    private Schema<?> resolveRef(Schema<?> schema) {
        String ref = schema.get$ref();
        if (ref == null || openAPI == null
                || openAPI.getComponents() == null
                || openAPI.getComponents().getSchemas() == null) {
            return schema;
        }
        // 形如 #/components/schemas/Pet → Pet
        int idx = ref.lastIndexOf('/');
        String name = idx >= 0 ? ref.substring(idx + 1) : ref;
        Schema<?> resolved = openAPI.getComponents().getSchemas().get(name);
        return resolved != null ? resolved : schema;
    }
}
