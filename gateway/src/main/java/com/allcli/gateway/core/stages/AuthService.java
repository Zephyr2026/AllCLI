package com.allcli.gateway.core.stages;

import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.core.InvokeContext;
import com.allcli.gateway.model.Subscription;
import com.allcli.gateway.persistence.SubscriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Step 2：鉴权。
 *
 * <p>一期 agent token 简化为 {@code Bearer agent_<agentId>}（无签名，仅标识身份；生产换 SSO/JWT）。
 * 校验 Subscription where agentId×toolId status=approved；scope 细化留后续。
 */
@Component
public class AuthService {

    private final SubscriptionRepository subRepo;

    @Autowired
    public AuthService(SubscriptionRepository subRepo) {
        this.subRepo = subRepo;
    }

    /**
     * 从 Authorization 头解析 agentId。
     * @return agentId；头缺失/格式错抛 AUTH_DENIED。
     */
    public String authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer agent_")) {
            throw GatewayException.of("AUTH_DENIED", "auth", "缺少或非法的 Authorization 头", false);
        }
        return authorization.substring("Bearer agent_".length()).trim();
    }

    /** 校验 agent 是否已订阅且订阅有效。 */
    public void authorize(InvokeContext ctx) {
        Subscription sub = subRepo
                .findByAgentIdAndToolId(ctx.getAgentId(), ctx.getTool().getToolId())
                .orElse(null);
        if (sub == null || !"approved".equals(sub.getStatus())) {
            throw GatewayException.of("AUTH_DENIED", "auth",
                    "未订阅或订阅未批准: agent=" + ctx.getAgentId(), false);
        }
        // M2 仅校验 approved；scope（数据域）细化留后续
    }
}
