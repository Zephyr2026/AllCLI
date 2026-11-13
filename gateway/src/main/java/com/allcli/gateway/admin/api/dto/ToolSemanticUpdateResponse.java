package com.allcli.gateway.admin.api.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** 语义编辑响应。 */
@Getter
@Builder
public class ToolSemanticUpdateResponse {

    private String toolId;

    private String version;

    private String status;

    /** advisory 警告码列表（不阻断保存） */
    private List<String> warnings;
}
