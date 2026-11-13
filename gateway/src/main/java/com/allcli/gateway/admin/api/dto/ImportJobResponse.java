package com.allcli.gateway.admin.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** 导入 job 状态（提交与查询共用）。 */
@Getter
@Builder
public class ImportJobResponse {

    private String jobId;

    /** running|done|failed */
    private String status;

    private List<String> created;

    private List<String> conflicts;

    private String errorCode;

    private String errorMessage;

    private LocalDateTime submittedAt;

    private LocalDateTime finishedAt;

    public static ImportJobResponse of(com.allcli.gateway.admin.core.ImportJob j) {
        return ImportJobResponse.builder()
                .jobId(j.getJobId())
                .status(j.getStatus())
                .created(j.getCreated())
                .conflicts(j.getConflicts())
                .errorCode(j.getErrorCode())
                .errorMessage(j.getErrorMessage())
                .submittedAt(j.getSubmittedAt())
                .finishedAt(j.getFinishedAt())
                .build();
    }
}
