package com.allcli.gateway.catalog.api.dto;

import lombok.Getter;
import lombok.Setter;

/** POST /api/v1/tools/{toolId}/publish 请求体（FR-REG-01）。 */
@Getter
@Setter
public class PublishRequest {

    /** 目标状态：published | gray。 */
    private String target;

    /** 灰度百分比（target=gray 时必填，1..100）。 */
    private Integer grayPercent;

    /** 变更说明，写入版本快照。 */
    private String changelog;
}
