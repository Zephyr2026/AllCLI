package com.allcli.gateway.core.stages;

import com.allcli.gateway.core.GatewayUtil;
import com.allcli.gateway.core.InvokeContext;
import com.allcli.gateway.model.Invocation;
import com.allcli.gateway.persistence.InvocationRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Step 9：审计。写 sys_invocation 元信息（明文不入库，仅存 SHA256 digest）。
 *
 * <p>明细审计入 ClickHouse（后续，经 MQ 异步）；M2 先落 MySQL 元信息。
 */
@Component
public class AuditWriter {

    private final InvocationRepository invocationRepo;

    @Autowired
    public AuditWriter(InvocationRepository invocationRepo) {
        this.invocationRepo = invocationRepo;
    }

    /** 在 pipeline 早期写入 hitl_pending 记录（HITL 挂起时）。 */
    public Invocation writePending(InvokeContext ctx) {
        return save(ctx, "hitl_pending", null);
    }

    /** pipeline 完成后写入最终记录（success/fail）；HITL 续跑时更新已存在的挂起记录。 */
    public Invocation writeFinal(InvokeContext ctx) {
        String status = ctx.getFinalStatus() != null ? ctx.getFinalStatus() : "success";
        return save(ctx, status, ctx.getErrorCode());
    }

    private Invocation save(InvokeContext ctx, String status, String errorCode) {
        // upsert：按 invocation_id 判存亡（HITL 续跑复用同一 invocation 记录）
        Invocation existing = invocationRepo.findByInvocationId(ctx.getInvocationId()).orElse(null);
        Invocation inv = existing != null ? existing : Invocation.builder()
                .invocationId(ctx.getInvocationId())
                .agentId(ctx.getAgentId())
                .toolId(ctx.getTool().getToolId())
                .traceId(ctx.getTraceId())
                .createdAt(java.time.LocalDateTime.now())
                .build();
        inv.setTraceId(ctx.getTraceId());
        inv.setInputDigest(ctx.getInputDigest());
        inv.setOutputDigest(ctx.getOutputDigest());
        inv.setStatus(status);
        inv.setDurationMs(ctx.getDurationMs());
        inv.setApprovalId(ctx.getApprovalId());
        inv.setErrorCode(errorCode);
        return invocationRepo.save(inv);
    }
}
