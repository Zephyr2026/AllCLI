package com.allcli.gateway.admin.api.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** 指标看板（FR-OPS-01 一期口径）。所有窗口边界由服务端计算。 */
@Getter
@Builder
public class MetricsResponse {

    private long systemCount;

    private ToolsStats tools;

    private InvocationStats invocations;

    private ReuseStats reuse;

    /** 工具按状态计数。 */
    @Getter
    @Builder
    public static class ToolsStats {
        private long total;
        private long draft;
        private long reviewing;
        private long published;
        private long gray;
        private long deprecated;
    }

    /** 时间窗调用统计。hitl=曾进入 HITL 的调用数（approvalId 非空，含已放行）；hitlRate=hitl/total。 */
    @Getter
    @Builder
    public static class WindowStats {
        private long total;
        private long success;
        private long fail;
        private long pendingApproval;
        private long hitl;
        private double hitlRate;
        private Double avgDurationMs;
    }

    /** 近 1 分钟窗口（QPS 近似）。 */
    @Getter
    @Builder
    public static class QpsStats {
        private long count;
        private double qps;
    }

    @Getter
    @Builder
    public static class InvocationStats {
        private WindowStats today;
        private WindowStats last7d;
        private QpsStats last1m;
    }

    /**
     * 复用率（FR-REG-04 一期口径，全量累计无窗口）：usedTools=被调用过的工具数，
     * reusedTools=被 ≥2 个 Agent 调用过的工具数，reuseRate=reusedTools/usedTools
     * （分母用已投入使用工具——未被调用的工具谈复用无意义；totalTools 一并返回供前端换算）。
     */
    @Getter
    @Builder
    public static class ReuseStats {
        private long totalTools;
        private long usedTools;
        private long reusedTools;
        private double reuseRate;
        private List<String> reusedToolIds;
    }
}
