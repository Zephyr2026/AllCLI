package com.allcli.gateway.mcp;

import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.api.dto.InvokeData;
import com.allcli.gateway.api.dto.InvokeRequest;
import com.allcli.gateway.core.GatewayUtil;
import com.allcli.gateway.core.InvokePipeline;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * MCP tools/call → 网关 invoke 流水线（D12：同进程零旁路，鉴权/限流/HITL/幂等/
 * 凭证/脱敏/审计全部复用）。
 *
 * <p>结果映射（对齐 PRD §8/§11.2 错误结构 {code,message,category,retriable}）：
 * succeeded → 正常 tool result；pending_approval → 正常 tool result（内含 approvalId
 * 与审批指引，模型可直接转述用户）；GatewayException → isError=true 的结构化错误。
 */
@Component
public class McpToolInvoker {

    private final InvokePipeline pipeline;
    private final McpAgentResolver agentResolver;

    public McpToolInvoker(InvokePipeline pipeline, McpAgentResolver agentResolver) {
        this.pipeline = pipeline;
        this.agentResolver = agentResolver;
    }

    public McpSchema.CallToolResult call(String toolId, McpSchema.CallToolRequest request) {
        String traceId = GatewayUtil.id("trace");
        try {
            String agentId = agentResolver.resolve();
            InvokeData data = pipeline.execute(traceId, agentId, InvokeRequest.builder()
                    .toolName(toolId)
                    .arguments(request.arguments())
                    .build());

            Map<String, Object> content = new LinkedHashMap<>();
            content.put("status", data.getStatus());
            content.put("invocationId", data.getInvocationId());
            if ("pending_approval".equals(data.getStatus())) {
                content.put("approvalId", data.getApprovalId());
                content.put("message", "该操作需人工审批（HITL）。请用户审批后重试："
                        + "POST /api/v1/hitl/" + data.getApprovalId() + "/decision"
                        + " {\"decision\":\"approved\",\"confirmIrreversible\":true}"
                        + "（irreversible 需二次确认；rejected 则取消）");
            } else {
                content.put("result", data.getResult());
            }
            return new McpSchema.CallToolResult(toJson(content), false);
        } catch (GatewayException e) {
            return error(traceId, e.getCode(), e.getMessage(), e.getCategory(), e.isRetriable());
        } catch (Exception e) {
            return error(traceId, "INTERNAL_ERROR", String.valueOf(e.getMessage()),
                    "business", true);
        }
    }

    private McpSchema.CallToolResult error(String traceId, String code, String message,
                                           String category, boolean retriable) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);
        err.put("category", category);
        err.put("retriable", retriable);
        err.put("traceId", traceId);
        return new McpSchema.CallToolResult(toJson(err), true);
    }

    private static String toJson(Object o) {
        try {
            return GatewayUtil.SORTED_MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return "{\"code\":\"INTERNAL_ERROR\",\"message\":\"result serialize failed\"}";
        }
    }
}
