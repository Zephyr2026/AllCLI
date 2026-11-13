package com.allcli.gateway.sandbox.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** POST /api/v1/tools/{toolId}/sandbox 请求体（可整体省略）。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SandboxRequest {

    /** 试运行入参；缺省时依次回退：examples[0] → inputSchema 生成。 */
    private Map<String, Object> arguments;
}
