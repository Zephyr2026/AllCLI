package com.allcli.gateway.core.stages;

import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.core.GatewayUtil;
import com.allcli.gateway.core.InvokeContext;
import com.allcli.gateway.model.Invocation;
import com.allcli.gateway.persistence.InvocationRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Step 6：幂等检查。写操作 5 分钟窗口内同 (agent,tool,inputDigest) 命中 → IDEMPOTENT_REPLAY。
 *
 * <p>以 inputDigest（arguments 的 SHA256）作为幂等指纹；命中返回首次结果（M2 暂返回提示，
 * 因明文结果不入库，完整重放需查 ClickHouse 明细，留后续）。
 */
@Component
public class IdempotencyChecker {

    private final InvocationRepository invRepo;
    private final int windowMinutes;

    public IdempotencyChecker(InvocationRepository invRepo,
                              @Value("${allcli.gateway.idempotency.window-minutes:5}") int windowMinutes) {
        this.invRepo = invRepo;
        this.windowMinutes = windowMinutes;
    }

    public void check(InvokeContext ctx) {
        // 只读工具不强制幂等；写操作若未带 idempotencyKey 则跳过（quality gate 应已拦截，网关不阻断）
        if (!"write".equals(ctx.getTool().getSideEffect())) {
            return;
        }
        Invocation prev = findRecent(ctx, "success");
        if (prev != null) {
            throw GatewayException.of("IDEMPOTENT_REPLAY", "business",
                    "幂等命中，返回首次结果（invocationId=" + prev.getInvocationId() + "）", false);
        }
    }

    /**
     * 近窗内同 (agent,tool,inputDigest) 且指定状态的最近一次调用；无则 null。
     * DIRECT 路径查 success（重放）；HITL 路径另查 hitl_pending（重复提交返回同一审批单，不重复挂起）。
     */
    public Invocation findRecent(InvokeContext ctx, String status) {
        if (!"write".equals(ctx.getTool().getSideEffect())) {
            return null;
        }
        if (ctx.getInputDigest() == null) {
            ctx.setInputDigest(GatewayUtil.digest(ctx.getRequest().getArguments()));
        }
        Invocation prev = invRepo
                .findFirstByAgentIdAndToolIdAndInputDigestAndStatusOrderByIdDesc(
                        ctx.getAgentId(), ctx.getTool().getToolId(), ctx.getInputDigest(), status)
                .orElse(null);
        if (prev != null && prev.getCreatedAt() != null
                && prev.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(windowMinutes))) {
            return prev;
        }
        return null;
    }
}
