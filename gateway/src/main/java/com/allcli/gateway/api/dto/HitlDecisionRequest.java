package com.allcli.gateway.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** POST /api/v1/hitl/{approvalId}/decision 请求体。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HitlDecisionRequest {

    /** approved|rejected */
    private String decision;

    private String reason;

    /** irreversible 操作 approved 时必传 true */
    private Boolean confirmIrreversible;
}
