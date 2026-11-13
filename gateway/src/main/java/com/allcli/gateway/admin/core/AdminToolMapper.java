package com.allcli.gateway.admin.core;

import com.allcli.gateway.model.ToolDefinition;
import org.springframework.stereotype.Component;

/** engine ToolDefinition → gateway ToolDefinition（业务字段拷贝，不拷自增 id；两实体同构）。 */
@Component
public class AdminToolMapper {

    public ToolDefinition toGatewayTool(com.allcli.engine.model.ToolDefinition src) {
        return ToolDefinition.builder()
                .toolId(src.getToolId())
                .name(src.getName())
                .version(src.getVersion())
                .status(src.getStatus())
                .systemRef(src.getSystemRef())
                .credentialRef(src.getCredentialRef())
                .description(src.getDescription())
                .endpoint(src.getEndpoint())
                .inputSchema(src.getInputSchema())
                .outputSchema(src.getOutputSchema())
                .sideEffect(src.getSideEffect())
                .reversibility(src.getReversibility())
                .idempotencyKey(src.getIdempotencyKey())
                .requiredPermission(src.getRequiredPermission())
                .hitl(src.getHitl())
                .examples(src.getExamples())
                .owner(src.getOwner())
                .build();
    }
}
