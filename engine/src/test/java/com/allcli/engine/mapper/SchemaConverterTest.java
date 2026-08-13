package com.allcli.engine.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * SchemaConverter 单测：覆盖原 Go schemaToMap 的全部边界。
 */
class SchemaConverterTest {

    private final SchemaConverter converter = new SchemaConverter(null);

    @Test
    void nullSchemaReturnsEmptyMap() {
        assertThat(converter.toMap(null)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingTypeFallsBackToString() {
        // 无 type 仅有 properties 的 schema —— 因 inferSchemaType=false，须由我们兜底为 string
        Schema<?> schema = new Schema<>();
        schema.setProperties(Map.of("x", new StringSchema()));
        Map<String, Object> map = converter.toMap(schema);

        assertThat(map.get("type")).isEqualTo("string");
    }

    @Test
    @SuppressWarnings("unchecked")
    void objectRecursesPropertiesAndRequired() {
        ObjectSchema pet = new ObjectSchema();
        pet.addProperties("id", new Schema<Integer>().type("integer"));
        pet.addProperties("name", new Schema<String>().type("string"));
        pet.addRequiredItem("id");
        pet.addRequiredItem("name");

        Map<String, Object> map = converter.toMap(pet);

        assertThat(map.get("type")).isEqualTo("object");
        Map<String, Object> props = (Map<String, Object>) map.get("properties");
        assertThat(props).containsOnlyKeys("id", "name");
        assertThat(((Map<String, Object>) props.get("id")).get("type")).isEqualTo("integer");
        assertThat(map.get("required")).isEqualTo(List.of("id", "name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void arrayRecursesItems() {
        ArraySchema array = new ArraySchema();
        array.setItems(new StringSchema());

        Map<String, Object> map = converter.toMap(array);

        assertThat(map.get("type")).isEqualTo("array");
        Map<String, Object> items = (Map<String, Object>) map.get("items");
        assertThat(items.get("type")).isEqualTo("string");
    }

    @Test
    void enumIsEmittedWhenPresent() {
        StringSchema s = new StringSchema();
        s.setEnum(List.of("cat", "dog"));
        Map<String, Object> map = converter.toMap(s);
        assertThat(map.get("enum")).isEqualTo(List.of("cat", "dog"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void refIsResolvedManuallyWhenPresent() {
        ObjectSchema pet = new ObjectSchema();
        pet.addProperties("name", new StringSchema());

        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(new Components());
        openAPI.getComponents().addSchemas("Pet", pet);

        Schema<?> refSchema = new Schema<>();
        refSchema.set$ref("#/components/schemas/Pet");

        SchemaConverter withRoot = new SchemaConverter(openAPI);
        Map<String, Object> map = withRoot.toMap(refSchema);

        assertThat(map.get("type")).isEqualTo("object");
        assertThat(((Map<String, Object>) map.get("properties"))).containsKey("name");
    }

    @Test
    void descriptionIsEmittedWhenPresent() {
        StringSchema s = new StringSchema();
        s.setDescription("宠物名");
        assertThat(converter.toMap(s).get("description")).isEqualTo("宠物名");
    }
}
