package com.allcli.gateway.admin.api.dto;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * PUT /api/v1/tools/{toolId} 请求体（FR-SEM-01~04 语义编辑）。
 * 合并语义：非 null 字段才覆盖，未出现保持原值；hitl 出现则整体替换。
 * （代价：无法显式清空 idempotencyKey——一期接受，见 ToolSemanticService 注释。）
 */
@Getter
@Setter
public class ToolSemanticUpdateRequest {

    private String description;

    private List<String> requiredPermission;

    /** readonly|write */
    private String sideEffect;

    /** readonly|reversible|irreversible */
    private String reversibility;

    private String idempotencyKey;

    /** {enabled, approverType, approverRef, timeoutSec}，出现即整体替换 */
    private Map<String, Object> hitl;
}
