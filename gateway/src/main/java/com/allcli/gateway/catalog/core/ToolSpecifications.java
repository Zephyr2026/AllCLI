package com.allcli.gateway.catalog.core;

import com.allcli.gateway.model.ToolDefinition;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * 目录检索条件（FR-REG-02）：q 模糊匹配 toolId/name/description（大小写不敏感，
 * 转义 %/_），system/reversibility/status 精确匹配。
 */
public final class ToolSpecifications {

    private ToolSpecifications() {
    }

    public static Specification<ToolDefinition> withFilters(
            String q, String system, String reversibility, String status) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = "%" + escapeLike(q.trim()) + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("toolId")), like, '\\'),
                        cb.like(cb.lower(root.get("name")), like, '\\'),
                        cb.like(cb.lower(root.get("description")), like, '\\')));
            }
            if (system != null && !system.isBlank()) {
                ps.add(cb.equal(root.get("systemRef"), system.trim()));
            }
            if (reversibility != null && !reversibility.isBlank()) {
                ps.add(cb.equal(root.get("reversibility"), reversibility.trim()));
            }
            if (status != null && !status.isBlank()) {
                ps.add(cb.equal(root.get("status"), status.trim()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").toLowerCase();
    }
}
