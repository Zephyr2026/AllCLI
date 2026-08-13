package com.allcli.engine.parser;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Operation;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * OpenApiLoader 解析冒烟测试：验证 swagger-parser API 链跑通。
 *
 * <p>读取 petstore.yaml，断言 3 个 operation（listPets/createPet/deletePet）及其关键字段可见。
 */
class OpenApiLoaderTest {

    @Test
    void parsesPetstoreWithThreeOperations() {
        OpenAPI openAPI = OpenApiLoader.load("src/test/resources/petstore.yaml");

        assertThat(openAPI.getPaths()).containsKeys("/pets", "/pets/{petId}");

        // GET /pets → listPets
        PathItem pets = openAPI.getPaths().get("/pets");
        Operation get = pets.getGet();
        assertThat(get.getOperationId()).isEqualTo("listPets");
        assertThat(get.getParameters()).hasSize(1);
        assertThat(get.getParameters().get(0).getName()).isEqualTo("limit");

        // POST /pets → createPet，requestBody 引用的 Pet 应被内联（resolveFully）
        Operation post = pets.getPost();
        assertThat(post.getOperationId()).isEqualTo("createPet");
        assertThat(post.getRequestBody()).isNotNull();
        assertThat(post.getRequestBody().getRequired()).isTrue();

        // DELETE /pets/{petId} → deletePet
        Operation delete = openAPI.getPaths().get("/pets/{petId}").getDelete();
        assertThat(delete.getOperationId()).isEqualTo("deletePet");

        // 遍历方法数 = 3（验证 readOperationsMap 行为）
        long total = openAPI.getPaths().values().stream()
                .mapToLong(pi -> pi.readOperationsMap().size())
                .sum();
        assertThat(total).isEqualTo(3L);

        // 验证 HttpMethod 枚举可取
        Map<PathItem.HttpMethod, Operation> methods = pets.readOperationsMap();
        assertThat(methods).containsKeys(PathItem.HttpMethod.GET, PathItem.HttpMethod.POST);
    }
}
