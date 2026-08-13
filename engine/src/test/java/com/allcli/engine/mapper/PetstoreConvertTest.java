package com.allcli.engine.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.allcli.engine.model.ConvertRequest;
import com.allcli.engine.model.ConvertResult;
import com.allcli.engine.model.ToolDefinition;
import com.allcli.engine.parser.OpenApiLoader;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * petstore.yaml 端到端字段断言（programmatic）—— 对标原 Go 版输出。
 *
 * <p>断言三个工具 listPets / createPet / deletePet 的标量字段与 input/output schema 结构。
 */
@SuppressWarnings("unchecked")
class PetstoreConvertTest {

    private List<ToolDefinition> tools;

    @BeforeEach
    void convert() {
        OpenAPI openAPI = OpenApiLoader.load("src/test/resources/petstore.yaml");
        ConvertRequest req = ConvertRequest.builder()
                .input("petstore.yaml")
                .systemRef("petstore")
                .owner("alice")
                .version("1.0.0")
                .timeoutMs(30000L)
                .build();
        ConvertResult result = new ToolMapper(openAPI).map(openAPI, req);
        tools = result.getTools();
    }

    @Test
    void threeToolsInDocumentOrder() {
        assertThat(tools).hasSize(3);
        assertThat(result(tools).getCount()).isEqualTo(3);
        assertThat(tools).extracting(ToolDefinition::getToolId)
                .containsExactly("listPets", "createPet", "deletePet");
    }

    @Test
    void listPetsIsReadonlyWithoutHitl() {
        ToolDefinition t = byId("listPets");
        assertThat(t.getSideEffect()).isEqualTo("readonly");
        assertThat(t.getReversibility()).isEqualTo("readonly");
        assertThat(t.getHitl()).isNull();
        assertThat(t.getEndpoint()).contains(entry("method", "GET"), entry("path", "/pets"));
    }

    @Test
    void listPetsInputHasLimitQuery() {
        ToolDefinition t = byId("listPets");
        Map<String, Object> input = t.getInputSchema();
        assertThat(input.get("type")).isEqualTo("object");
        Map<String, Object> props = (Map<String, Object>) input.get("properties");
        assertThat(props).containsOnlyKeys("limit");
        assertThat(((Map<String, Object>) props.get("limit")).get("type")).isEqualTo("integer");
        assertThat(input.get("required")).isEqualTo(List.of());
    }

    @Test
    void listPetsOutputIs200ArrayOfPet() {
        ToolDefinition t = byId("listPets");
        Map<String, Object> out = t.getOutputSchema();
        assertThat(out).containsOnlyKeys("200");
        Map<String, Object> arr = (Map<String, Object>) out.get("200");
        assertThat(arr.get("type")).isEqualTo("array");
        Map<String, Object> items = (Map<String, Object>) arr.get("items");
        assertThat(items.get("type")).isEqualTo("object");
        Map<String, Object> petProps = (Map<String, Object>) items.get("properties");
        assertThat(petProps).containsOnlyKeys("id", "name", "tag");
        assertThat(items.get("required")).isEqualTo(List.of("id", "name"));
    }

    @Test
    void createPetIsWriteReversibleWithHitl() {
        ToolDefinition t = byId("createPet");
        assertThat(t.getSideEffect()).isEqualTo("write");
        assertThat(t.getReversibility()).isEqualTo("reversible");
        assertThat(t.getHitl()).contains(
                entry("enabled", true),
                entry("approverType", "owner"),
                entry("approverRef", "alice"),
                entry("timeoutSec", 600));
    }

    @Test
    void createPetInputBodyIsInlinePet() {
        ToolDefinition t = byId("createPet");
        Map<String, Object> input = t.getInputSchema();
        Map<String, Object> props = (Map<String, Object>) input.get("properties");
        assertThat(props).containsOnlyKeys("body");
        Map<String, Object> body = (Map<String, Object>) props.get("body");
        assertThat(body.get("type")).isEqualTo("object");
        assertThat(((Map<String, Object>) body.get("properties"))).containsOnlyKeys("id", "name", "tag");
        assertThat(body.get("required")).isEqualTo(List.of("id", "name"));
        assertThat(input.get("required")).isEqualTo(List.of("body"));
    }

    @Test
    void createPetOutput201IsEmpty() {
        ToolDefinition t = byId("createPet");
        assertThat(t.getOutputSchema()).containsOnlyKeys("201");
        assertThat((Map<String, Object>) t.getOutputSchema().get("201")).isEmpty();
    }

    @Test
    void deletePetIsWriteIrreversible() {
        ToolDefinition t = byId("deletePet");
        assertThat(t.getSideEffect()).isEqualTo("write");
        assertThat(t.getReversibility()).isEqualTo("irreversible");
        assertThat(t.getHitl()).isNotNull();
        assertThat(t.getEndpoint()).contains(entry("method", "DELETE"), entry("path", "/pets/{petId}"));
    }

    @Test
    void deletePetInputHasRequiredPetId() {
        ToolDefinition t = byId("deletePet");
        Map<String, Object> props = (Map<String, Object>) t.getInputSchema().get("properties");
        assertThat(props).containsOnlyKeys("petId");
        assertThat(((Map<String, Object>) props.get("petId")).get("type")).isEqualTo("string");
        assertThat(t.getInputSchema().get("required")).isEqualTo(List.of("petId"));
    }

    private ToolDefinition byId(String id) {
        return tools.stream().filter(t -> id.equals(t.getToolId())).findFirst().orElseThrow();
    }

    private ConvertResult result(List<ToolDefinition> t) {
        return new ConvertResult(t);
    }
}
