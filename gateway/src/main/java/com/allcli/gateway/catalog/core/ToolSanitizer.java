package com.allcli.gateway.catalog.core;

import com.allcli.gateway.catalog.api.dto.ToolDetailResponse;
import com.allcli.gateway.catalog.api.dto.ToolSummaryResponse;
import com.allcli.gateway.model.ToolDefinition;

/**
 * 目录脱敏映射（FR-REG-02/NFR-SEC-01）：endpoint（上游地址）与 credentialRef（凭证引用）
 * 不出目录；其余治理字段保留供 Agent 决策。
 */
public final class ToolSanitizer {

    private ToolSanitizer() {
    }

    public static ToolSummaryResponse toSummary(ToolDefinition t) {
        return ToolSummaryResponse.builder()
                .toolId(t.getToolId())
                .name(t.getName())
                .version(t.getVersion())
                .status(t.getStatus())
                .systemRef(t.getSystemRef())
                .description(t.getDescription())
                .sideEffect(t.getSideEffect())
                .reversibility(t.getReversibility())
                .hitlEnabled(hitlEnabled(t))
                .owner(t.getOwner())
                .build();
    }

    public static ToolDetailResponse toDetail(ToolDefinition t) {
        return ToolDetailResponse.builder()
                .toolId(t.getToolId())
                .name(t.getName())
                .version(t.getVersion())
                .status(t.getStatus())
                .systemRef(t.getSystemRef())
                .description(t.getDescription())
                .sideEffect(t.getSideEffect())
                .reversibility(t.getReversibility())
                .hitlEnabled(hitlEnabled(t))
                .owner(t.getOwner())
                .inputSchema(t.getInputSchema())
                .outputSchema(t.getOutputSchema())
                .requiredPermission(t.getRequiredPermission())
                .idempotencyKey(t.getIdempotencyKey())
                .hitl(t.getHitl())
                .examples(t.getExamples())
                .build();
    }

    private static boolean hitlEnabled(ToolDefinition t) {
        return t.getHitl() != null && Boolean.TRUE.equals(t.getHitl().get("enabled"));
    }
}
