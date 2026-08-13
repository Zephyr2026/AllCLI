package com.allcli.gateway.core;

import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.api.dto.InvokeRequest;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 单次 invoke 在流水线各 stage 间传递的上下文。
 *
 * <p>每步读取/写入所需中间态；pipeline 末据其组装响应、写审计。
 */
@Getter
@Setter
@Builder
public class InvokeContext {

    private final String traceId;
    private final String agentId;
    private final InvokeRequest request;
    private final long startedAtMillis;

    private ToolDefinition tool;

    /** 是否需 HITL（分级结果） */
    private boolean hitlRequired;

    /** 流水线中途挂起（HITL），不再继续后续 stage */
    private boolean suspended;

    private String invocationId;
    private String approvalId;

    private Object upstreamResult;
    private String inputDigest;
    private String outputDigest;
    private int durationMs;
    private String finalStatus;   // success|hitl_pending|fail
    private String errorCode;

    public static InvokeContext create(String traceId, String agentId, InvokeRequest request) {
        return InvokeContext.builder()
                .traceId(traceId)
                .agentId(agentId)
                .request(request)
                .startedAtMillis(System.currentTimeMillis())
                .build();
    }
}
