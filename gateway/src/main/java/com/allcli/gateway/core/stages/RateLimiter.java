package com.allcli.gateway.core.stages;

import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.core.InvokeContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Duration;

/**
 * Step 4：限流。Redis 固定窗口计数，维度 agentId×toolId。
 *
 * <p>超限 → RATE_LIMITED(retriable)。
 */
@Component
public class RateLimiter {

    private final StringRedisTemplate redis;
    private final int windowSeconds;
    private final int maxRequests;

    @Autowired
    public RateLimiter(StringRedisTemplate redis,
                       @Value("${allcli.gateway.rate-limit.window-seconds:60}") int windowSeconds,
                       @Value("${allcli.gateway.rate-limit.max-requests:100}") int maxRequests) {
        this.redis = redis;
        this.windowSeconds = windowSeconds;
        this.maxRequests = maxRequests;
    }

    public void check(InvokeContext ctx) {
        String key = "rl:" + ctx.getAgentId() + ":" + ctx.getTool().getToolId();
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, Duration.ofSeconds(windowSeconds));
        }
        if (count != null && count > maxRequests) {
            throw GatewayException.of("RATE_LIMITED", "limit",
                    "触发限流: " + ctx.getAgentId() + "/" + ctx.getTool().getToolId()
                            + " 超过 " + maxRequests + "/" + windowSeconds + "s", true);
        }
    }
}
