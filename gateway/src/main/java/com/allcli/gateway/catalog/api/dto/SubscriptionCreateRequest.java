package com.allcli.gateway.catalog.api.dto;

import lombok.Getter;
import lombok.Setter;

/** POST /api/v1/subscriptions 请求体（FR-REG-03）。 */
@Getter
@Setter
public class SubscriptionCreateRequest {

    /** 订阅方 Agent 标识。 */
    private String agentId;

    /** 目标工具。 */
    private String toolId;

    /** 申请范围（系统/数据域，一期仅记录）。 */
    private String scope;
}
