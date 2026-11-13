package com.allcli.gateway.admin.api.dto;

import lombok.Builder;
import lombok.Getter;

/** 系统注册响应。 */
@Getter
@Builder
public class SystemResponse {

    private String systemId;

    private String name;

    private String baseUrl;

    private String authType;

    private String owner;

    private String status;
}
