package com.allcli.gateway.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 确定性 mock 生成器：schema → 值的映射规则（纯 Java，不起 Spring）。 */
class MockValueGeneratorTest {

    @Test
    void nullOrEmptySchemaYieldsNull() {
        assertThat(MockValueGenerator.outputOf(null)).isNull();
        assertThat(MockValueGenerator.outputOf(Map.of())).isNull();
        // 204 空响应体：响应码解包后为空 schema → null（调用方兜底）
        assertThat(MockValueGenerator.outputOf(Map.of("204", Map.of()))).isNull();
    }

    @Test
    void responseCodeKeyUnwrappedToFirst2xx() {
        Map<String, Object> schema = Map.of(
                "404", Map.of("type", "object", "properties", Map.of("error", Map.of("type", "string"))),
                "200", Map.of("type", "object", "properties", Map.of("id", Map.of("type", "integer"))));
        Object out = MockValueGenerator.outputOf(schema);
        assertThat(out).isEqualTo(Map.of("id", 42));
    }

    @Test
    void petstoreLikeArrayOutput() {
        // 对标 golden：listPets outputSchema = {"200": {type:array, items:{...pet}}}
        Map<String, Object> pet = Map.of(
                "type", "object",
                "properties", Map.of(
                        "id", Map.of("type", "integer"),
                        "name", Map.of("type", "string"),
                        "tag", Map.of("type", "string")),
                "required", List.of("id", "name"));
        Map<String, Object> schema = Map.of("200", Map.of("type", "array", "items", pet));

        Object out = MockValueGenerator.outputOf(schema);
        assertThat(out).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Object> arr = (List<Object>) out;
        assertThat(arr).hasSize(2);
        assertThat(arr.get(0)).isEqualTo(Map.of("id", 42, "name", "mock_name", "tag", "mock_tag"));
    }

    @Test
    void scalarTypesAndEnumAndDefault() {
        assertThat(MockValueGenerator.outputOf(Map.of("type", "integer"))).isEqualTo(42);
        assertThat(MockValueGenerator.outputOf(Map.of("type", "number"))).isEqualTo(3.14);
        assertThat(MockValueGenerator.outputOf(Map.of("type", "boolean"))).isEqualTo(true);
        assertThat(MockValueGenerator.outputOf(Map.of("type", "string")))
                .isEqualTo("mock_value");
        assertThat(MockValueGenerator.outputOf(
                Map.of("type", "string", "enum", List.of("cat", "dog"))))
                .isEqualTo("cat");
        assertThat(MockValueGenerator.outputOf(
                Map.of("type", "string", "default", "fallback")))
                .isEqualTo("fallback");
    }

    @Test
    void stringFormats() {
        assertThat(MockValueGenerator.outputOf(Map.of("type", "string", "format", "date")))
                .isEqualTo("2026-01-01");
        assertThat(MockValueGenerator.outputOf(Map.of("type", "string", "format", "email")))
                .isEqualTo("mock@example.com");
    }

    @Test
    void nestedObjectWithNamedProperties() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "orderId", Map.of("type", "string"),
                        "paid", Map.of("type", "boolean")));
        assertThat(MockValueGenerator.outputOf(schema))
                .isEqualTo(Map.of("orderId", "mock_orderId", "paid", true));
    }

    @Test
    void sampleArgumentsIncludesAllProperties() {
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "limit", Map.of("type", "integer"),
                        "tag", Map.of("type", "string")),
                "required", List.of("limit"));
        assertThat(MockValueGenerator.sampleArguments(inputSchema))
                .isEqualTo(Map.of("limit", 42, "tag", "mock_tag"));
    }

    @Test
    void sampleArgumentsEmptySchemaYieldsEmptyMap() {
        assertThat(MockValueGenerator.sampleArguments(null)).isEmpty();
        assertThat(MockValueGenerator.sampleArguments(Map.of())).isEmpty();
    }
}
