package com.allcli.gateway.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 网关统一异常：携带错误码/分类/是否可重试，由全局处理器转成 ApiResponse。 */
@Getter
public class GatewayException extends RuntimeException {

    private final String code;
    private final String category;  // auth|limit|validation|business|upstream|hitl
    private final boolean retriable;

    public GatewayException(String code, String category, String message, boolean retriable) {
        super(message);
        this.code = code;
        this.category = category;
        this.retriable = retriable;
    }

    public static GatewayException of(String code, String category, String message, boolean retriable) {
        return new GatewayException(code, category, message, retriable);
    }
}
