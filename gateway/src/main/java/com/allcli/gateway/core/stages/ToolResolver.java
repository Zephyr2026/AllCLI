package com.allcli.gateway.core.stages;

import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.core.InvokeContext;
import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.persistence.ToolDefinitionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Step 1：解析 toolName+version → ToolDefinition。
 */
@Component
public class ToolResolver {

    private final ToolDefinitionRepository toolRepo;

    @Autowired
    public ToolResolver(ToolDefinitionRepository toolRepo) {
        this.toolRepo = toolRepo;
    }

    public ToolDefinition resolve(InvokeContext ctx) {
        String name = ctx.getRequest().getToolName();
        String version = ctx.getRequest().getVersion();
        ToolDefinition tool = (version == null || version.isEmpty())
                ? toolRepo.findByToolId(name).orElse(null)
                : toolRepo.findByToolIdAndVersion(name, version).orElse(null);
        if (tool == null) {
            throw GatewayException.of("TOOL_NOT_FOUND", "validation",
                    "工具不存在: " + name + (version == null ? "" : "@" + version), false);
        }
        ctx.setTool(tool);
        return tool;
    }
}
