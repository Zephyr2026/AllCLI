package com.allcli.gateway.core;

import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.api.dto.HitlDecisionRequest;
import com.allcli.gateway.api.dto.InvokeData;
import com.allcli.gateway.api.dto.InvokeRequest;
import com.allcli.gateway.core.stages.AuditWriter;
import com.allcli.gateway.core.stages.AuthService;
import com.allcli.gateway.core.stages.ClassifyDecider;
import com.allcli.gateway.core.stages.IdempotencyChecker;
import com.allcli.gateway.core.stages.IntentValidator;
import com.allcli.gateway.core.stages.RateLimiter;
import com.allcli.gateway.core.stages.ResultNormalizer;
import com.allcli.gateway.core.stages.ToolResolver;
import com.allcli.gateway.core.stages.UpstreamInvoker;
import com.allcli.gateway.model.Approval;
import com.allcli.gateway.model.Invocation;
import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.persistence.InvocationRepository;
import com.allcli.gateway.persistence.ToolDefinitionRepository;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * invoke 10 步流水线主编排（PRD §7.2）。
 *
 * <p>前半段（1-5）：解析→鉴权→意图→限流→分级。分级为 DIRECT 则进入后半段；
 * 为 HITL/HITL_CONFIRM 则挂起（HitlService.suspend）返回 HITL_PENDING。
 * 后半段（7-9）：转发→归一化脱敏→审计。HITL approved 时由 resume 触发后半段。
 *
 * <p>挂起调用的原始 request 明文暂存内存（M2 demo；生产应加密持久化或查 ClickHouse 明细）。
 */
@Service
public class InvokePipeline {

    private final ToolResolver toolResolver;
    private final AuthService authService;
    private final IntentValidator intentValidator;
    private final RateLimiter rateLimiter;
    private final IdempotencyChecker idempotencyChecker;
    private final ClassifyDecider classifyDecider;
    private final UpstreamInvoker upstreamInvoker;
    private final ResultNormalizer resultNormalizer;
    private final AuditWriter auditWriter;
    private final HitlService hitlService;
    private final InvocationRepository invocationRepo;
    private final ToolDefinitionRepository toolRepo;

    /** approvalId → 挂起的原始 request（内存暂存，M2） */
    private final ConcurrentMap<String, InvokeRequest> suspendedRequests = new ConcurrentHashMap<>();

    @Autowired
    public InvokePipeline(ToolResolver toolResolver, AuthService authService,
                          IntentValidator intentValidator, RateLimiter rateLimiter,
                          IdempotencyChecker idempotencyChecker, ClassifyDecider classifyDecider,
                          UpstreamInvoker upstreamInvoker, ResultNormalizer resultNormalizer,
                          AuditWriter auditWriter, @Lazy HitlService hitlService,
                          InvocationRepository invocationRepo, ToolDefinitionRepository toolRepo) {
        this.toolResolver = toolResolver;
        this.authService = authService;
        this.intentValidator = intentValidator;
        this.rateLimiter = rateLimiter;
        this.idempotencyChecker = idempotencyChecker;
        this.classifyDecider = classifyDecider;
        this.upstreamInvoker = upstreamInvoker;
        this.resultNormalizer = resultNormalizer;
        this.auditWriter = auditWriter;
        this.hitlService = hitlService;
        this.invocationRepo = invocationRepo;
        this.toolRepo = toolRepo;
    }

    /** 主入口。 */
    public InvokeData execute(String traceId, String agentId, InvokeRequest request) {
        InvokeContext ctx = InvokeContext.create(traceId, agentId, request);
        ctx.setInvocationId(GatewayUtil.id("inv"));

        try {
            toolResolver.resolve(ctx);    // 1
            authService.authorize(ctx);   // 2
            intentValidator.validate(ctx);// 3
            rateLimiter.check(ctx);       // 4
            ClassifyDecider.Decision decision = classifyDecider.decide(ctx); // 5

            if (decision == ClassifyDecider.Decision.DIRECT) {
                idempotencyChecker.check(ctx); // 6（只读通常跳过）
                return runSecondHalf(ctx);
            }

            // HITL：挂起前先幂等查重（近窗同 agent+tool+inputDigest）
            //   已 success → 重放异常；挂起中 → 返回同一审批单（重复提交不重复挂起）
            Invocation prevDone = idempotencyChecker.findRecent(ctx, "success");
            if (prevDone != null) {
                throw GatewayException.of("IDEMPOTENT_REPLAY", "business",
                        "幂等命中，返回首次结果（invocationId=" + prevDone.getInvocationId() + "）", false);
            }
            Invocation prevPending = idempotencyChecker.findRecent(ctx, "hitl_pending");
            if (prevPending != null && prevPending.getApprovalId() != null) {
                return InvokeData.builder()
                        .status("pending_approval")
                        .invocationId(prevPending.getInvocationId())
                        .approvalId(prevPending.getApprovalId())
                        .build();
            }

            boolean requireConfirm = decision == ClassifyDecider.Decision.HITL_CONFIRM;
            String approvalId = hitlService.suspend(ctx, requireConfirm);
            suspendedRequests.put(approvalId, request);

            return InvokeData.builder()
                    .status("pending_approval")
                    .invocationId(ctx.getInvocationId())
                    .approvalId(approvalId)
                    .build();
        } catch (RuntimeException e) {
            failAudit(ctx, request, e);
            throw e;
        }
    }

    /** HITL approved 后从第 7 步续跑。 */
    public InvokeData resume(String approvalId) {
        Approval approval = hitlService.findApproval(approvalId);
        InvokeRequest request = suspendedRequests.remove(approvalId);
        if (request == null) {
            throw GatewayException.of("HITL_REJECTED", "hitl",
                    "挂起请求已失效（进程重启或已处理）", false);
        }
        Invocation inv = invocationRepo.findByInvocationId(approval.getInvocationId()).orElseThrow(() ->
                GatewayException.of("HITL_REJECTED", "hitl", "关联调用不存在", false));
        ToolDefinition tool = toolRepo.findByToolId(inv.getToolId()).orElseThrow();

        InvokeContext ctx = InvokeContext.create(inv.getTraceId(), inv.getAgentId(), request);
        ctx.setInvocationId(inv.getInvocationId());
        ctx.setTool(tool);
        ctx.setApprovalId(approvalId);
        return runSecondHalf(ctx);
    }

    /** 后半段：转发→归一化脱敏→审计→返回。 */
    private InvokeData runSecondHalf(InvokeContext ctx) {
        Object raw = upstreamInvoker.invoke(ctx);          // 7
        Object normalized = resultNormalizer.normalize(raw); // 8
        ctx.setUpstreamResult(normalized);
        ctx.setOutputDigest(GatewayUtil.digest(normalized));
        if (ctx.getInputDigest() == null) {
            ctx.setInputDigest(GatewayUtil.digest(ctx.getRequest().getArguments()));
        }
        ctx.setFinalStatus("success");
        ctx.setDurationMs((int) (System.currentTimeMillis() - ctx.getStartedAtMillis()));
        auditWriter.writeFinal(ctx);                        // 9

        return InvokeData.builder()
                .status("succeeded")
                .result(normalized)
                .invocationId(ctx.getInvocationId())
                .build();
    }

    private void failAudit(InvokeContext ctx, InvokeRequest request, RuntimeException e) {
        if (ctx.getInputDigest() == null) {
            ctx.setInputDigest(GatewayUtil.digest(request.getArguments()));
        }
        ctx.setFinalStatus("fail");
        ctx.setDurationMs((int) (System.currentTimeMillis() - ctx.getStartedAtMillis()));
        if (e instanceof GatewayException ge) {
            ctx.setErrorCode(ge.getCode());
        }
        if (ctx.getTool() != null) {
            auditWriter.writeFinal(ctx);
        }
    }
}
