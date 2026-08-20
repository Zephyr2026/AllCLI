package com.allcli.gateway.catalog.api.dto;

import lombok.Builder;
import lombok.Getter;

/** 目录列表行（FR-REG-02）：脱敏后的摘要，不含 endpoint/credentialRef/schema。 */
@Getter
@Builder
public class ToolSummaryResponse {

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
}
