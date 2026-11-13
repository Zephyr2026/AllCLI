package com.allcli.gateway.admin.api.dto;

import lombok.Builder;
import lombok.Getter;

/** 凭证注册响应（不含 secret 与 secret_ref——密文引用也不回显，防猜测探测）。 */
@Getter
@Builder
public class CredentialResponse {

    private String credId;

    private String systemRef;

    private String type;

    private String rotatePolicy;

    private String status;
}
