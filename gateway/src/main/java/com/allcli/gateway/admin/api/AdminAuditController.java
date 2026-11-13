package com.allcli.gateway.admin.api;

import com.allcli.gateway.admin.api.dto.AuditEntry;
import com.allcli.gateway.admin.core.AuditQueryService;
import com.allcli.gateway.admin.core.CsvExporter;
import com.allcli.gateway.api.dto.ApiResponse;
import com.allcli.gateway.catalog.api.dto.PageResponse;
import com.allcli.gateway.core.GatewayUtil;
import com.allcli.gateway.model.Invocation;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计查询（admin 面，FR-GOV-01）：按调用方/工具/时间/可逆性检索调用记录 + CSV 导出。
 * 记录本身只含元信息与 digest（明文从不入库），无二次脱敏需求。
 * 鉴权由 AdminAuthInterceptor 拦截（Bearer admin_xxx）。
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AdminAuditController {

    private final AuditQueryService auditQueryService;

    public AdminAuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    /** 调用记录检索：agentId/toolId 等值 + from/to（ISO 本地时间）+ reversibility（子查询）+ 分页。 */
    @GetMapping
    public ApiResponse list(@RequestParam(required = false) String agentId,
                            @RequestParam(required = false) String toolId,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
                            @RequestParam(required = false) String reversibility,
                            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                            Pageable pageable,
                            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Page<Invocation> page = auditQueryService.query(agentId, toolId, from, to, reversibility, pageable);
        return ApiResponse.ok(PageResponse.of(page.map(AuditEntry::of)),
                traceId == null || traceId.isEmpty() ? GatewayUtil.id("trace") : traceId);
    }

    /** CSV 导出：过滤同列表，时间倒序，上限 {@value AuditQueryService#EXPORT_LIMIT} 行（截断时尾行标注）。 */
    @GetMapping(value = "/export.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) String agentId,
                                            @RequestParam(required = false) String toolId,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
                                            @RequestParam(required = false) String reversibility) {
        List<Invocation> rows = auditQueryService.exportRows(agentId, toolId, from, to, reversibility);
        boolean truncated = auditQueryService.count(agentId, toolId, from, to, reversibility)
                > AuditQueryService.EXPORT_LIMIT;
        String csv = CsvExporter.export(rows, truncated);
        // UTF-8 BOM：Excel 直接打开中文不乱码
        byte[] body = ('﻿' + csv).getBytes(StandardCharsets.UTF_8);
        String filename = "audit-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}
