package com.allcli.gateway.core.stages;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.api.dto.InvokeRequest;
import com.allcli.gateway.core.InvokeContext;
import com.allcli.gateway.model.ToolDefinition;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 意图校验单测：锁定 DefaultJsonSchemaValidator（networknt 2.x）对 draft-07 风格
 * {type,properties,required} schema 的拒绝/放行语义（M3 校验器迁移后的行为基线）。
 */
class IntentValidatorTest {

    private final IntentValidator validator = new IntentValidator();

    private InvokeContext ctx(Map<String, Object> schema, Map<String, Object> args) {
        ToolDefinition tool = new ToolDefinition();
        tool.setInputSchema(schema);
        InvokeContext ctx = InvokeContext.create("trace_x", "agent_t",
                InvokeRequest.builder().toolName("t").arguments(args).build());
        ctx.setTool(tool);
        return ctx;
    }

    @Test
    void validArgumentsPass() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("limit", Map.of("type", "integer")),
                "required", java.util.List.of("limit"));
        assertThatCode(() -> validator.validate(ctx(schema, Map.of("limit", 11))))
                .doesNotThrowAnyException();
    }

    @Test
    void missingRequiredIsRejected() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("limit", Map.of("type", "integer")),
                "required", java.util.List.of("limit"));
        assertThatThrownBy(() -> validator.validate(ctx(schema, Map.of())))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("入参校验失败")
                .hasMessageContaining("limit");
    }

    @Test
    void wrongTypeIsRejected() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of("limit", Map.of("type", "integer")));
        assertThatThrownBy(() -> validator.validate(ctx(schema, Map.of("limit", "not-a-number"))))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("入参校验失败");
    }

    @Test
    void nullSchemaSkipsValidation() {
        assertThatCode(() -> validator.validate(ctx(null, Map.of())))
                .doesNotThrowAnyException();
    }
}
