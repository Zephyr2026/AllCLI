package com.allcli.gateway.core.stages;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.core.InvokeContext;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Step 3：意图校验。arguments vs tool.inputSchema（JSON Schema 结构校验）。
 *
 * <p>M2 先做结构校验（draft-07）；业务合理性校验留后续。
 */
@Component
public class IntentValidator {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchemaFactory factory =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    public void validate(InvokeContext ctx) {
        Map<String, Object> schema = ctx.getTool().getInputSchema();
        if (schema == null || schema.isEmpty()) {
            return; // 无 schema 则跳过
        }
        try {
            JsonNode schemaNode = mapper.valueToTree(schema);
            JsonNode dataNode = mapper.valueToTree(
                    ctx.getRequest().getArguments() == null ? Map.of() : ctx.getRequest().getArguments());
            JsonSchema jsonSchema = factory.getSchema(schemaNode, SchemaValidatorsConfig.builder().build());
            Set<ValidationMessage> errors = jsonSchema.validate(dataNode);
            if (!errors.isEmpty()) {
                throw GatewayException.of("INTENT_REJECTED", "validation",
                        "入参校验失败: " + errors.iterator().next().getMessage(), false);
            }
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            throw GatewayException.of("INTENT_REJECTED", "validation",
                    "意图校验异常: " + e.getMessage(), false);
        }
    }
}
