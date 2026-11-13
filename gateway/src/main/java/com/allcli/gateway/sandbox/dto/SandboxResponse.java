package com.allcli.gateway.sandbox.dto;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 沙箱试运行结果（FR-AGENT-03）。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SandboxResponse {

    private String toolId;
    private String version;
    private String sideEffect;

    /** 恒为 succeeded（沙箱失败走错误信封）。 */
    private String status;

    /** 恒为 true，标识非真实调用。 */
    private boolean sandbox;

    /** 入参来源：request|example|generated。 */
    private String argSource;

    /** 实际使用的入参。 */
    private Map<String, Object> arguments;

    /** 由 outputSchema 生成的 mock 响应。 */
    private Object result;

    /** 沙箱行为说明（未触达真实系统等）。 */
    private List<String> notes;
}
