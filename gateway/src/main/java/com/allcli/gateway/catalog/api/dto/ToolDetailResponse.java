package com.allcli.gateway.catalog.api.dto;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/** 目录详情（FR-REG-02）：摘要 + Schema/权限/HITL/示例，供 Agent 预览与对齐入参。 */
@Getter
@Builder
public class ToolDetailResponse {

    private String toolId;

    private String name;

    private String version;

    private String status;

    private String systemRef;

    private String description;

    private String sideEffect;

    private String reversibility;

    private boolean hitlEnabled;

    private String owner;

    private Map<String, Object> inputSchema;

    private Map<String, Object> outputSchema;

    private List<String> requiredPermission;

    private String idempotencyKey;

    private Map<String, Object> hitl;

    private List<Map<String, Object>> examples;
}
