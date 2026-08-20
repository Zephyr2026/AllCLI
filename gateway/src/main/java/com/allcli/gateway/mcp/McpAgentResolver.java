package com.allcli.gateway.mcp;

import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.core.stages.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * MCP 会话 → agentId：优先取当前 HTTP 请求的 Authorization 头（SYNC WebMVC 下
 * tool handler 与请求同线程）；无头时回退配置的演示 agent（allcli.mcp.default-agent，
 * 为空则拒绝）。Claude Code 侧经 `claude mcp add --header` 传入。
 */
@Component
public class McpAgentResolver {

    private final AuthService authService;
    private final String defaultAgent;

    public McpAgentResolver(AuthService authService,
                            @Value("${allcli.mcp.default-agent:}") String defaultAgent) {
        this.authService = authService;
        this.defaultAgent = defaultAgent;
    }

    public String resolve() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            String authorization = request.getHeader("Authorization");
            if (authorization != null && !authorization.isBlank()) {
                return authService.authenticate(authorization);
            }
        }
        if (defaultAgent != null && !defaultAgent.isBlank()) {
            return defaultAgent.trim();
        }
        throw GatewayException.of("AUTH_DENIED", "auth",
                "MCP 调用缺少 Authorization: Bearer agent_<id> 头", false);
    }
}
