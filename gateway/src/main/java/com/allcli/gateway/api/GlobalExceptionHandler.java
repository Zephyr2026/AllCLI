package com.allcli.gateway.api;

import com.allcli.gateway.api.dto.ApiResponse;
import com.allcli.gateway.api.dto.GatewayException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 全局异常处理：GatewayException → 统一错误信封。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GatewayException.class)
    public ApiResponse handle(GatewayException e) {
        return ApiResponse.error(e.getCode(), e.getCategory(), e.getMessage(), e.isRetriable(), null);
    }
}
