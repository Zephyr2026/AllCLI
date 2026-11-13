package com.allcli.gateway.admin.core;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 导入 job（内存态，重启即丢——PRD 一期口径）。running→done|failed 单向。
 */
@Getter
@Setter
public class ImportJob {

    private String jobId;

    private String systemRef;

    /** running|done|failed */
    private String status = "running";

    /** 新建落库的 toolId。 */
    private List<String> created = List.of();

    /** 已存在被跳过的 toolId（FR-CONV-03 冲突检测，不覆盖）。 */
    private List<String> conflicts = List.of();

    private String errorCode;

    private String errorMessage;

    private LocalDateTime submittedAt = LocalDateTime.now();

    private LocalDateTime finishedAt;
}
