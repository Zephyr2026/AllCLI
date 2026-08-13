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
        String inputDigest = GatewayUtil.digest(ctx.getRequest().getArguments());
        ctx.setInputDigest(inputDigest);

        Invocation prev = invRepo
                .findFirstByAgentIdAndToolIdAndInputDigestAndStatusOrderByIdDesc(
                        ctx.getAgentId(), ctx.getTool().getToolId(), inputDigest, "success")
                .orElse(null);
        if (prev != null && prev.getCreatedAt() != null
                && prev.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(windowMinutes))) {
            throw GatewayException.of("IDEMPOTENT_REPLAY", "business",
                    "幂等命中，返回首次结果（invocationId=" + prev.getInvocationId() + "）", false);
        }
    }
}
