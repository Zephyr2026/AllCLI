package com.allcli.gateway.persistence;

import com.allcli.gateway.model.Invocation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvocationRepository extends JpaRepository<Invocation, Long>, JpaSpecificationExecutor<Invocation> {

    Optional<Invocation> findByInvocationId(String invocationId);

    /** 幂等：按 agentId×toolId×idempotencyKey（M2 用 input_digest 近似查找近期成功记录） */
    Optional<Invocation> findFirstByAgentIdAndToolIdAndInputDigestAndStatusOrderByIdDesc(
            String agentId, String toolId, String inputDigest, String status);

    /**
     * 窗口聚合（FR-OPS-01）：总数/成功/失败/挂起/曾进 HITL（approvalId 非空——upsert 语义下已放行的行
     * status 已变 success，只能凭 approvalId 识别）/平均耗时。时间边界由 Java 侧传入，不用 DB NOW()。
     * 行内为 [Long total, Long success, Long fail, Long pending, Long hitl, Double avgMs]，空窗口 sum/avg 为 null。
     */
    @Query("""
            select count(i),
                   sum(case when i.status = 'success' then 1 else 0 end),
                   sum(case when i.status = 'fail' then 1 else 0 end),
                   sum(case when i.status = 'hitl_pending' then 1 else 0 end),
                   sum(case when i.approvalId is not null then 1 else 0 end),
                   avg(i.durationMs)
            from Invocation i where i.createdAt >= :from
            """)
    List<Object[]> aggregateSince(@Param("from") LocalDateTime from);

    /** 复用率口径（FR-REG-04）：每个工具被多少个不同 Agent 调用过（全量累计，不设窗口）。 */
    @Query("select i.toolId, count(distinct i.agentId) from Invocation i group by i.toolId")
    List<Object[]> agentCountPerTool();
}
