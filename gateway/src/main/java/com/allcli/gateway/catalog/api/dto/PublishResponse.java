package com.allcli.gateway.catalog.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** publish/deprecate 响应。 */
@Getter
@Builder
@AllArgsConstructor
public class PublishResponse {

    private String toolId;

    private String version;

    private String status;

    /** 版本快照行 id（deprecate 无快照写入，为 null）。 */
    private Long snapshotId;
}
