package com.allcli.gateway.admin.core;

import com.allcli.gateway.model.Invocation;
import com.allcli.gateway.model.ToolDefinition;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * 审计检索条件（FR-GOV-01）：agentId/toolId 等值、createdAt 时间区间、
 * reversibility 经 sys_tool_definition 子查询过滤（sys_invocation 无该列）。
 * 模式仿 {@link com.allcli.gateway.catalog.core.ToolSpecifications}。
 */
public final class InvocationSpecifications {

    private InvocationSpecifications() {
    }

    public static Specification<Invocation> withFilters(
            String agentId, String toolId, LocalDateTime from, LocalDateTime to, String reversibility) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (agentId != null && !agentId.isBlank()) {
                ps.add(cb.equal(root.get("agentId"), agentId.trim()));
            }
            if (toolId != null && !toolId.isBlank()) {
                ps.add(cb.equal(root.get("toolId"), toolId.trim()));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (reversibility != null && !reversibility.isBlank()) {
                Subquery<String> sq = query.subquery(String.class);
                Root<ToolDefinition> t = sq.from(ToolDefinition.class);
                sq.select(t.get("toolId"))
                        .where(cb.equal(t.get("reversibility"), reversibility.trim()));
                ps.add(root.get("toolId").in(sq));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
