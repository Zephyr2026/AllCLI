package com.allcli.gateway.admin.core;

import com.allcli.gateway.admin.api.dto.MetricsResponse;
import com.allcli.gateway.persistence.InvocationRepository;
import com.allcli.gateway.persistence.SystemRepository;
import com.allcli.gateway.persistence.ToolDefinitionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 指标聚合（FR-OPS-01 一期最小口径）：系统数/工具按状态/调用窗口统计/复用率。
 *
 * <p>口径要点：
 * <ul>
 *   <li>HITL 命中数 = approvalId 非空的行数（sys_invocation 按 invocation_id upsert，
 *       已放行的行 status 已从 hitl_pending 变 success，不能按 status 统计）；hitlRate 分母=窗口总调用。
 *       （「写操作拦截率」需 join 工具定义判 side_effect，二期口径。）</li>
 *   <li>复用率为全量累计（复用是累计概念）；QPS=近 1 分钟计数/60 的近似。</li>
 *   <li>聚合直查 sys_invocation（走既有索引），无预聚合——一期数据量无压力，二期需预聚合。</li>
 * </ul>
 */
@Service
public class MetricsService {

    private static final List<String> TOOL_STATUSES = List.of(
            "draft", "reviewing", "published", "gray", "deprecated");

    private final SystemRepository systemRepository;
    private final ToolDefinitionRepository toolRepository;
    private final InvocationRepository invocationRepository;

    public MetricsService(SystemRepository systemRepository,
                          ToolDefinitionRepository toolRepository,
                          InvocationRepository invocationRepository) {
        this.systemRepository = systemRepository;
        this.toolRepository = toolRepository;
        this.invocationRepository = invocationRepository;
    }

    public MetricsResponse collect() {
        LocalDateTime now = LocalDateTime.now();
        return MetricsResponse.builder()
                .systemCount(systemRepository.count())
                .tools(toolStats())
                .invocations(MetricsResponse.InvocationStats.builder()
                        .today(windowStats(LocalDate.now().atStartOfDay()))
                        .last7d(windowStats(now.minusDays(7)))
                        .last1m(qpsStats(now.minusSeconds(60)))
                        .build())
                .reuse(reuseStats())
                .build();
    }

    private MetricsResponse.ToolsStats toolStats() {
        Map<String, Long> byStatus = new java.util.HashMap<>();
        long total = 0;
        for (Object[] row : toolRepository.countGroupByStatus()) {
            String status = String.valueOf(row[0]);
            long count = ((Number) row[1]).longValue();
            byStatus.put(status, count);
            total += count;
        }
        return MetricsResponse.ToolsStats.builder()
                .total(total)
                .draft(byStatus.getOrDefault("draft", 0L))
                .reviewing(byStatus.getOrDefault("reviewing", 0L))
                .published(byStatus.getOrDefault("published", 0L))
                .gray(byStatus.getOrDefault("gray", 0L))
                .deprecated(byStatus.getOrDefault("deprecated", 0L))
                .build();
    }

    private MetricsResponse.WindowStats windowStats(LocalDateTime from) {
        Object[] row = firstRow(from);
        long total = num(row[0]);
        long success = num(row[1]);
        long fail = num(row[2]);
        long pending = num(row[3]);
        long hitl = num(row[4]);
        Double avgMs = row[5] == null ? null : ((Number) row[5]).doubleValue();
        return MetricsResponse.WindowStats.builder()
                .total(total).success(success).fail(fail)
                .pendingApproval(pending).hitl(hitl)
                .hitlRate(total == 0 ? 0.0 : round(hitl * 1.0 / total, 4))
                .avgDurationMs(avgMs)
                .build();
    }

    private MetricsResponse.QpsStats qpsStats(LocalDateTime from) {
        Object[] row = firstRow(from);
        long count = num(row[0]);
        return MetricsResponse.QpsStats.builder()
                .count(count).qps(round(count / 60.0, 2))
                .build();
    }

    /** 聚合无 group by 恒返回一行（空窗口 sum/avg 为 null）；防御空列表。 */
    private Object[] firstRow(LocalDateTime from) {
        List<Object[]> rows = invocationRepository.aggregateSince(from);
        return rows.isEmpty() ? new Object[6] : rows.get(0);
    }

    private MetricsResponse.ReuseStats reuseStats() {
        List<String> reusedToolIds = new ArrayList<>();
        long usedTools = 0;
        long totalTools = toolRepository.count();
        for (Object[] row : invocationRepository.agentCountPerTool()) {
            usedTools++;
            if (((Number) row[1]).longValue() >= 2) {
                reusedToolIds.add(String.valueOf(row[0]));
            }
        }
        return MetricsResponse.ReuseStats.builder()
                .totalTools(totalTools)
                .usedTools(usedTools)
                .reusedTools(reusedToolIds.size())
                .reuseRate(usedTools == 0 ? 0.0 : round(reusedToolIds.size() * 1.0 / usedTools, 4))
                .reusedToolIds(reusedToolIds)
                .build();
    }

    private static long num(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }

    private static double round(double v, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(v * factor) / factor;
    }
}
