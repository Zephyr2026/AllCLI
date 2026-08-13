package com.allcli.engine.cli;

import com.allcli.engine.mapper.ToolMapper;
import com.allcli.engine.model.ConvertRequest;
import com.allcli.engine.model.ConvertResult;
import com.allcli.engine.parser.OpenApiLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * CLI 入口：解析参数 → 调 OpenApiLoader + ToolMapper → 输出 {@code {tools, count}} JSON。
 *
 * <p>参数：
 * <ul>
 *   <li>{@code --input} （必填）OpenAPI 文档路径或 URL</li>
 *   <li>{@code --system} 归属系统 ID</li>
 *   <li>{@code --owner} 工具负责人 / HITL approverRef</li>
 *   <li>{@code --version} 工具版本（默认 1.0.0）</li>
 *   <li>{@code --timeout} 调用超时毫秒（默认 30000）</li>
 *   <li>{@code --persist} 带此旗标则写回 sys_tool_definition（M1.5/持久化阶段）</li>
 * </ul>
 */
@Component
public class ConvertCommand implements ApplicationRunner {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS); // 按 key 字母序，便于人工 diff

    private final PersistHandler persistHandler;

    @Autowired
    public ConvertCommand(PersistHandler persistHandler) {
        this.persistHandler = persistHandler;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 仅当显式传入 --input 时才进入 CLI 转化逻辑；
        // 这样 @SpringBootTest 等 context 启动场景（无 args）不会误触发。
        if (!args.containsOption("input")) {
            return;
        }

        String input = required(args, "input");
        String timeoutStr = optional(args, "timeout");
        ConvertRequest req = ConvertRequest.builder()
                .input(input)
                .systemRef(optional(args, "system"))
                .owner(optional(args, "owner"))
                .version(firstOr(optional(args, "version"), "1.0.0"))
                .timeoutMs((timeoutStr == null || timeoutStr.isEmpty()) ? 30000L : Long.parseLong(timeoutStr))
                .build();

        OpenAPI openAPI = OpenApiLoader.load(input);
        ConvertResult result = new ToolMapper(openAPI).map(openAPI, req);

        if (args.containsOption("persist")) {
            persistHandler.persist(result.getTools());
        }

        // 输出信封 {tools, count}；tools 序列化时排除 JPA id
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("tools", result.getTools());
        envelope.put("count", result.getCount());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(System.out, envelope);
    }

    private String required(ApplicationArguments args, String key) {
        List<String> values = args.getOptionValues(key);
        if (values == null || values.isEmpty() || values.get(0) == null || values.get(0).isEmpty()) {
            throw new IllegalArgumentException("缺少必填参数 --" + key);
        }
        return values.get(0);
    }

    private String optional(ApplicationArguments args, String key) {
        List<String> values = args.getOptionValues(key);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }

    private String firstOr(String value, String fallback) {
        return (value == null || value.isEmpty()) ? fallback : value;
    }

    /** 占位接口：持久化阶段实现；M1 CLI 阶段提供 no-op 默认，避免写库逻辑耦合进 CLI。 */
    public interface PersistHandler {
        void persist(List<com.allcli.engine.model.ToolDefinition> tools);
    }
}
