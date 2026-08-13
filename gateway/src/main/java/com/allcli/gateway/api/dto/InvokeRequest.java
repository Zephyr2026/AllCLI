package com.allcli.gateway.api.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** POST /api/v1/invoke 请求体。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvokeRequest {

    /** 工具标识（toolId）或名称。 */
    private String toolName;

    /** 版本，可空（取最新）。 */
    private String version;

    /** 调用参数。 */
    private Map<String, Object> arguments;

    /** 幂等键，写操作必填。 */
    private String idempotencyKey;
}
