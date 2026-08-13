package com.allcli.engine.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.allcli.engine.mapper.ToolMapper;
import com.allcli.engine.model.ConvertRequest;
import com.allcli.engine.model.ToolDefinition;
import com.allcli.engine.parser.OpenApiLoader;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 写库闭环集成测试：连真实 MySQL（192.168.85.128:3306/allcli）。
 *
 * <p>跑 petstore 转化 → 写入 sys_tool_definition → 回查断言 → 清理测试数据。
 * 验证 6 个 JSON 列（endpoint/input_schema/output_schema/hitl 等）通过 Hibernate 6 正确落库。
 */
@SpringBootTest
class ToolDefinitionPersistTest {

    private static final String SYSTEM = "petstore-it";

    @Autowired
    private ToolDefinitionRepository repository;

    @Autowired
    private PersistService persistService;

    @AfterEach
    void cleanup() {
        // 每个测试后清掉本系统写入的工具，保持可重复运行
        List<ToolDefinition> rows = repository.findBySystemRef(SYSTEM);
        repository.deleteAll(rows);
    }

    @Test
    @SuppressWarnings("unchecked")
    void petstoreWritesThreeToolsWithCorrectInference() {
        OpenAPI openAPI = OpenApiLoader.load("src/test/resources/petstore.yaml");
        ConvertRequest req = ConvertRequest.builder()
                .input("petstore.yaml")
                .systemRef(SYSTEM)
                .owner("alice")
                .version("1.0.0")
                .timeoutMs(30000L)
                .build();
        List<ToolDefinition> tools = new ToolMapper(openAPI).map(openAPI, req).getTools();

        persistService.persist(tools);

        List<ToolDefinition> rows = repository.findBySystemRef(SYSTEM);
        assertThat(rows).hasSize(3);

        ToolDefinition listPets = findByToolId(rows, "listPets");
        assertThat(listPets.getSideEffect()).isEqualTo("readonly");
        assertThat(listPets.getReversibility()).isEqualTo("readonly");
        assertThat(listPets.getHitl()).isNull();
        assertThat(listPets.getEndpoint()).containsEntry("method", "GET");

        ToolDefinition createPet = findByToolId(rows, "createPet");
        assertThat(createPet.getSideEffect()).isEqualTo("write");
        assertThat(createPet.getReversibility()).isEqualTo("reversible");
        assertThat(createPet.getHitl()).containsEntry("approverRef", "alice");

        ToolDefinition deletePet = findByToolId(rows, "deletePet");
        assertThat(deletePet.getReversibility()).isEqualTo("irreversible");
    }

    @Test
    void upsertUpdatesExistingRowInsteadOfDuplicating() {
        OpenAPI openAPI = OpenApiLoader.load("src/test/resources/petstore.yaml");
        ConvertRequest req = ConvertRequest.builder()
                .input("petstore.yaml")
                .systemRef(SYSTEM)
                .owner("alice")
                .version("1.0.0")
                .timeoutMs(30000L)
                .build();
        List<ToolDefinition> tools = new ToolMapper(openAPI).map(openAPI, req).getTools();

        persistService.persist(tools);
        persistService.persist(tools); // 二次写同一批

        // upsert：仍 3 行，不重复
        assertThat(repository.findBySystemRef(SYSTEM)).hasSize(3);
    }

    private ToolDefinition findByToolId(List<ToolDefinition> rows, String id) {
        return rows.stream().filter(t -> id.equals(t.getToolId())).findFirst().orElseThrow();
    }
}
