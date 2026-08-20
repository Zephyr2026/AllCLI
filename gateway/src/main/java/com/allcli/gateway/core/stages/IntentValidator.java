package com.allcli.gateway.core.stages;

import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.core.InvokeContext;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.jackson2.DefaultJsonSchemaValidator;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Step 3：意图校验。arguments vs tool.inputSchema（JSON Schema 结构校验）。
 *
 * <p>校验器复用 MCP SDK 的 DefaultJsonSchemaValidator（底层 networknt json-schema-validator 2.x，
 * 与 spring-ai-starter-mcp-server 同一依赖树，避免 1.x/2.x 双版本冲突）。M2 先做结构校验；
 * 业务合理性校验留后续。
 */
@Component
public class IntentValidator {

    private final JsonSchemaValidator validator = new DefaultJsonSchemaValidator();

    public void validate(InvokeContext ctx) {
        Map<String, Object> schema = ctx.getTool().getInputSchema();
        if (schema == null || schema.isEmpty()) {
            return; // 无 schema 则跳过
        }
        try {
            JsonSchemaValidator.ValidationResponse resp = validator.validate(
                    schema,
                    ctx.getRequest().getArguments() == null ? Map.of() : ctx.getRequest().getArguments());
            if (!resp.valid()) {
                throw GatewayException.of("INTENT_REJECTED", "validation",
                        "入参校验失败: " + resp.errorMessage(), false);
            }
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw GatewayException.of("INTENT_REJECTED", "validation",
                    "意图校验异常: " + e.getMessage(), false);
        }
    }
}
