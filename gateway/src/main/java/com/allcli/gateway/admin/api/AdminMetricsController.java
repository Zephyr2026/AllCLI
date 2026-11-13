package com.allcli.gateway.admin.api;

import com.allcli.gateway.admin.api.dto.MetricsResponse;
import com.allcli.gateway.admin.core.MetricsService;
import com.allcli.gateway.api.dto.ApiResponse;
import com.allcli.gateway.core.GatewayUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 治理看板数据源（admin 面，FR-OPS-01 一期口径）。
 * Prometheus 侧指标见 /actuator/prometheus（QPS/延迟/JVM 基础）。
 * 鉴权由 AdminAuthInterceptor 拦截（Bearer admin_xxx）。
 */
@RestController
public class AdminMetricsController {

    private final MetricsService metricsService;

    public AdminMetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/api/v1/metrics")
    public ApiResponse metrics(@RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        MetricsResponse data = metricsService.collect();
        return ApiResponse.ok(data,
                traceId == null || traceId.isEmpty() ? GatewayUtil.id("trace") : traceId);
    }
}
