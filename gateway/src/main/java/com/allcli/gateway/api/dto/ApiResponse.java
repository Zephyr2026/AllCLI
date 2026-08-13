package com.allcli.gateway.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 统一响应信封（PRD §1.2）：{ code, message, data, traceId }。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse {

    private String code;       // OK 或错误码
    private String message;
    private Object data;
    private String traceId;

    /** retriable 仅错误时返回 */
    private String category;
    private Boolean retriable;

    public static ApiResponse ok(Object data, String traceId) {
        return ApiResponse.builder()
                .code("OK").data(data).traceId(traceId)
                .build();
    }

    public static ApiResponse error(String code, String category, String message, boolean retriable, String traceId) {
        return ApiResponse.builder()
                .code(code).category(category).message(message).retriable(retriable).traceId(traceId)
                .build();
    }
}
