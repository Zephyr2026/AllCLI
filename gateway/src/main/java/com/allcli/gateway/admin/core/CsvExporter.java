package com.allcli.gateway.admin.core;

import com.allcli.gateway.model.Invocation;
import java.util.List;

/**
 * 审计 CSV 导出（FR-GOV-01）：RFC4180 风格转义 + 公式注入兜底（=+-@ 开头前置 '）。
 * 值域全为 ID/枚举/digest，风险本低，兜底为纵深防御。
 */
public final class CsvExporter {

    private static final String HEADER = "createdAt,invocationId,traceId,agentId,toolId,status,"
            + "durationMs,errorCode,approvalId";

    private CsvExporter() {
    }

    public static String export(List<Invocation> rows, boolean truncated) {
        StringBuilder sb = new StringBuilder(rows.size() * 128 + 64);
        sb.append(HEADER).append("\r\n");
        for (Invocation i : rows) {
            sb.append(cell(i.getCreatedAt() == null ? "" : i.getCreatedAt().toString())).append(',')
              .append(cell(i.getInvocationId())).append(',')
              .append(cell(i.getTraceId())).append(',')
              .append(cell(i.getAgentId())).append(',')
              .append(cell(i.getToolId())).append(',')
              .append(cell(i.getStatus())).append(',')
              .append(cell(i.getDurationMs() == null ? "" : String.valueOf(i.getDurationMs()))).append(',')
              .append(cell(i.getErrorCode())).append(',')
              .append(cell(i.getApprovalId())).append("\r\n");
        }
        if (truncated) {
            sb.append("# truncated at ").append(AuditQueryService.EXPORT_LIMIT).append(" rows\r\n");
        }
        return sb.toString();
    }

    private static String cell(String raw) {
        String v = raw == null ? "" : raw;
        // 公式注入兜底：Excel/LibreOffice 会把 = + - @ 开头当公式
        if (!v.isEmpty() && (v.charAt(0) == '=' || v.charAt(0) == '+' || v.charAt(0) == '-' || v.charAt(0) == '@')) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
