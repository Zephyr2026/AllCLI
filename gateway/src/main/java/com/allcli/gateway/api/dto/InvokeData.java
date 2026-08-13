package com.allcli.gateway.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** invoke 返回的 data 部分。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvokeData {

    /** succeeded|pending_approval|replayed */
    private String status;

    /** 调用结果（脱敏后） */
    private Object result;

    private String invocationId;

    /** HITL 挂起时返回 */
    private String approvalId;

    private String expiresAt;
}
