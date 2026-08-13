package com.allcli.engine.model;

import lombok.Builder;
import lombok.Getter;

/**
 * 一次转化请求的上下文（由 CLI / 调用方传入）。
 *
 * <p>这些字段在 OpenAPI 文档里拿不到，需调用方补充：归属系统、凭证、负责人、版本等。
 */
@Getter
@Builder
public class ConvertRequest {

    /** OpenAPI 文档路径或 URL。 */
    private final String input;

    /** 归属存量系统业务 ID（→ system_id）。 */
    private final String systemRef;

    /** 凭证 ID（→ credential_id），可空。 */
    private final String credentialRef;

    /** 工具负责人；同时作为写操作 HITL 的 approverRef。 */
    private final String owner;

    /** 工具版本，默认 1.0.0。 */
    @Builder.Default
    private final String version = "1.0.0";

    /** 调用超时（毫秒），写入 endpoint.timeoutMs。 */
    @Builder.Default
    private final long timeoutMs = 30000L;
}
