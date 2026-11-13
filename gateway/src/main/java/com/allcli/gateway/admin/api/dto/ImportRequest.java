package com.allcli.gateway.admin.api.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** POST /api/v1/imports 请求体（FR-CONV-01：OpenAPI 导入，异步 job）。 */
@Getter
@Setter
public class ImportRequest {

    /** openapi（一期唯一支持类型）。 */
    private String sourceType;

    /** OpenAPI 文档 URL（与 sourceContent 二选一）。 */
    private String sourceUrl;

    /** OpenAPI 文档内容（YAML/JSON 文本，与 sourceUrl 二选一）。 */
    private String sourceContent;

    /** 归属系统（须已注册 POST /api/v1/systems）。 */
    private String systemRef;

    /** 凭证 ID，可空。 */
    private String credentialRef;

    /** 工具负责人（缺省取系统 owner，同时作为写操作 HITL approverRef）。 */
    private String owner;

    private String version;

    private Long timeoutMs;

    /** 仅导入 endpoint.path 以这些前缀开头的操作（空=全部）。 */
    private List<String> includePaths;

    /** 剔除匹配该正则的 path（可空）。 */
    private String excludeRegex;
}
