package com.allcli.gateway.admin.api;

import com.allcli.gateway.admin.api.dto.ImportJobResponse;
import com.allcli.gateway.admin.api.dto.ImportRequest;
import com.allcli.gateway.admin.core.ImportService;
import com.allcli.gateway.api.dto.ApiResponse;
import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.core.GatewayUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenAPI 导入（admin 面，FR-CONV-01/02/03）：异步 job + 冲突检测。
 * job 内存态、重启即丢（一期口径）。鉴权由 AdminAuthInterceptor 拦截。
 */
@RestController
@RequestMapping("/api/v1/imports")
public class AdminImportController {

    private final ImportService importService;

    public AdminImportController(ImportService importService) {
        this.importService = importService;
    }

    /** 提交导入（同步校验失败即返回错误码；成功返回 jobId）。 */
    @PostMapping
    public ApiResponse submit(@RequestBody ImportRequest req,
                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        traceId = traceId(traceId);
        try {
            return ApiResponse.ok(ImportJobResponse.of(importService.submit(req)), traceId);
        } catch (GatewayException e) {
            return ApiResponse.error(e.getCode(), e.getCategory(), e.getMessage(), e.isRetriable(), traceId);
        }
    }

    /** 查询 job 状态（running→done|failed；done 含 created/conflicts）。 */
    @GetMapping("/{jobId}")
    public ApiResponse status(@PathVariable String jobId,
                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        traceId = traceId(traceId);
        try {
            return ApiResponse.ok(ImportJobResponse.of(importService.status(jobId)), traceId);
        } catch (GatewayException e) {
            return ApiResponse.error(e.getCode(), e.getCategory(), e.getMessage(), e.isRetriable(), traceId);
        }
    }

    private String traceId(String traceId) {
        return (traceId == null || traceId.isEmpty()) ? GatewayUtil.id("trace") : traceId;
    }
}
