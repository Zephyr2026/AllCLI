package com.allcli.gateway.admin;

import com.allcli.gateway.api.dto.ApiResponse;
import com.allcli.gateway.core.GatewayUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理面（控制面）鉴权拦截器（M4，FR-CONV-04/FR-GOV-01 等管理 API 的门卫）。
 *
 * <p>token 约定：{@code Authorization: Bearer admin_<secret>}，secret 命中配置
 * {@code allcli.admin.tokens}（逗号分隔）即通过，身份恒为 admin（单角色，一期无 RBAC）。
 * 与 agent 鉴权（AuthService，Bearer agent_）按前缀天然分流，互不替代。
 *
 * <p>注册在 {@code /api/v1/**}，仅对本类判定的管理面路径生效——目录检索/invoke/订阅申请等
 * 公共与 agent 面路径直接放行（秒过，不做任何工作）。
 *
 * <p>鉴权失败返回 <b>HTTP 200 + 信封 code=AUTH_DENIED</b>（项目惯例：业务错误一律 200 靠
 * code 区分，客户端与测试无需分支）。REST 语义上应为 401，M5 统一硬化（已知限制）。
 */
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String TOKEN_PREFIX = "Bearer admin_";

    private final Set<String> adminTokens;

    public AdminAuthInterceptor(@Value("${allcli.admin.tokens:}") String adminTokensCsv) {
        this.adminTokens = Arrays.stream(adminTokensCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws IOException {
        if (!requiresAdmin(request)) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null
                && authorization.startsWith(TOKEN_PREFIX)
                && adminTokens.contains(authorization.substring(TOKEN_PREFIX.length()))) {
            return true;
        }
        deny(response, authorization);
        return false;
    }

    /** 管理面路径判定：前缀即 admin 的资源 + tools 的 PUT（语义编辑）+ subscriptions 的 GET 列表与 revoke。 */
    static boolean requiresAdmin(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (path.startsWith("/api/v1/audit") || path.startsWith("/api/v1/metrics")
                || path.startsWith("/api/v1/imports") || path.startsWith("/api/v1/credentials")
                || path.startsWith("/api/v1/systems") || path.startsWith("/api/v1/approvals")) {
            return true;
        }
        if ("PUT".equals(method) && path.startsWith("/api/v1/tools/")) {
            return true;
        }
        if (path.startsWith("/api/v1/subscriptions")
                && ("GET".equals(method) || path.endsWith("/revoke"))) {
            return true;
        }
        return false;
    }

    private void deny(HttpServletResponse response, String authorization) throws IOException {
        String hint = (authorization == null || authorization.isBlank())
                ? "管理面接口需要 admin token（Authorization: Bearer admin_xxx）"
                : "管理面接口不接受该 token（需要 Bearer admin_xxx）";
        ApiResponse body = ApiResponse.error("AUTH_DENIED", "auth", hint, false,
                GatewayUtil.id("trace"));
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(GatewayUtil.SORTED_MAPPER.writeValueAsBytes(body));
    }
}
