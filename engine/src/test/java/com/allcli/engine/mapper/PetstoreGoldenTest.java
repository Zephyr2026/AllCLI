package com.allcli.engine.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.allcli.engine.model.ConvertRequest;
import com.allcli.engine.model.ConvertResult;
import com.allcli.engine.parser.OpenApiLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.models.OpenAPI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Golden 回归测试：序列化转化结果为 JSON，与 {@code petstore.expected.json} 做语义树比较。
 *
 * <p>使用 {@link JsonNode#equals}（顺序无关的语义比较），规避 map key 顺序差异。
 * 若 golden 文件不存在则首次生成（基线定稿），存在则严格比对。
 */
class PetstoreGoldenTest {

    private static final Path GOLDEN = Path.of("src/test/resources/petstore.expected.json");

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    @Test
    void matchesGolden() throws Exception {
        OpenAPI openAPI = OpenApiLoader.load("src/test/resources/petstore.yaml");
        ConvertRequest req = ConvertRequest.builder()
                .input("petstore.yaml")
                .systemRef("petstore")
                .owner("alice")
                .version("1.0.0")
                .timeoutMs(30000L)
                .build();
        ConvertResult result = new ToolMapper(openAPI).map(openAPI, req);

        ObjectNode envelope = mapper.createObjectNode();
        envelope.set("tools", mapper.valueToTree(result.getTools()));
        envelope.put("count", result.getCount());

        if (!Files.exists(GOLDEN)) {
            // 首次：生成基线并断言其本身有效
            Files.writeString(GOLDEN, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(envelope));
            throw new IllegalStateException("Golden 基线不存在，已首次生成于 " + GOLDEN
                    + "，请人工核对后重跑测试。");
        }

        JsonNode expected = mapper.readTree(Files.readString(GOLDEN));
        JsonNode actual = mapper.readTree(mapper.writeValueAsString(envelope));
        // JsonNode.equals 为顺序无关的语义树比较
        if (!actual.equals(expected)) {
            // 失败时打印归一化后的人类可读差异，便于定位
            String a = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(actual);
            String e = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(expected);
            throw new AssertionError("golden 不匹配\n--- expected ---\n" + e + "\n--- actual ---\n" + a);
        }
    }
}
