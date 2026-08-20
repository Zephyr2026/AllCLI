package com.allcli.gateway.mcp;

import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.persistence.ToolDefinitionRepository;
import io.modelcontextprotocol.server.McpServerFeatures;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * MCP 工具注册表：启动时从 sys_tool_definition 读 published 工具构建规格；
 * 目录变更事件到达后按需重建增量（见 McpCatalogRefresher）。
 * 一期只暴露 published（gray 灰度路由未实现，见 M3 风险清单）。
 */
@Component
public class McpToolRegistry {

    private final ToolDefinitionRepository toolRepo;
    private final McpToolFactory factory;
    private final McpToolInvoker invoker;

    public McpToolRegistry(ToolDefinitionRepository toolRepo,
                           McpToolFactory factory, McpToolInvoker invoker) {
        this.toolRepo = toolRepo;
        this.factory = factory;
        this.invoker = invoker;
    }

    /** 全量构建当前 published 工具的 MCP 规格（callHandler 闭包绑定 toolId）。 */
    public List<McpServerFeatures.SyncToolSpecification> buildSpecifications() {
        return toolRepo.findByStatus("published").stream()
                .map(this::specificationOf)
                .toList();
    }

    /** 构建单个工具规格；目录刷新（addTool）复用。 */
    public McpServerFeatures.SyncToolSpecification specificationOf(ToolDefinition tool) {
        String toolId = tool.getToolId();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(factory.toTool(tool))
                .callHandler((exchange, request) -> invoker.call(toolId, request))
                .build();
    }
}
