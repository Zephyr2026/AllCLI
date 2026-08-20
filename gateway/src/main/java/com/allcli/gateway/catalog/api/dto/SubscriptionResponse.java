package com.allcli.gateway.catalog.api.dto;

import lombok.Builder;
import lombok.Getter;

/** 订阅响应。 */
@Getter
@Builder
public class SubscriptionResponse {

    private String subId;

    private String agentId;

    private String toolId;

    private String scope;

    /** pending|approved|rejected|revoked */
    private String status;

    private String approvedBy;
}
