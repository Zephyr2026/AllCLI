package com.allcli.gateway.sandbox.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** FR-SEM-05 语义质量自检结果（advisory：一期发布门禁仅强制 reversibility）。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QualityReport {

    private String toolId;

    /** pass|warn|fail（任一 fail 即 fail，否则有 warn 即 warn）。 */
    private String overall;

    private List<QualityItem> items;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QualityItem {

        /** description|inputSchema|examples|writeGuard。 */
        private String item;

        /** pass|warn|fail。 */
        private String status;

        private String detail;
    }
}
