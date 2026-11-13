package com.allcli.gateway.admin.api.dto;

import lombok.Getter;
import lombok.Setter;

/** POST /api/v1/systems 请求体（FR-CONV 前置：存量系统注册）。 */
@Getter
@Setter
public class SystemRegisterRequest {

    /** 系统唯一标识（如 petstore）。 */
    private String systemId;

    private String name;

    /** 网关转发目标前缀。 */
    private String baseUrl;

    /** OAuth2|ApiKey|Cookie|None */
    private String authType;

    private String owner;
}
