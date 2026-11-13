package com.allcli.gateway.admin.core;

import com.allcli.gateway.model.Invocation;
import com.allcli.gateway.persistence.InvocationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * 审计查询（FR-GOV-01）：按调用方/工具/时间/可逆性检索调用记录。
 * 导出与列表同一套过滤，导出上限 {@value #EXPORT_LIMIT} 行（一期内存态口径）。
 */
@Service
public class AuditQueryService {

    public static final int EXPORT_LIMIT = 1000;

    private final InvocationRepository invocationRepository;

    public AuditQueryService(InvocationRepository invocationRepository) {
        this.invocationRepository = invocationRepository;
    }

    public Page<Invocation> query(String agentId, String toolId,
                                  LocalDateTime from, LocalDateTime to,
                                  String reversibility, Pageable pageable) {
        return invocationRepository.findAll(
                InvocationSpecifications.withFilters(agentId, toolId, from, to, reversibility), pageable);
    }

    /** 导出取数：过滤同列表，按时间倒序，最多 {@value #EXPORT_LIMIT} 行。 */
    public List<Invocation> exportRows(String agentId, String toolId,
                                       LocalDateTime from, LocalDateTime to,
                                       String reversibility) {
        Page<Invocation> page = invocationRepository.findAll(
                InvocationSpecifications.withFilters(agentId, toolId, from, to, reversibility),
                PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt")));
        return page.getContent();
    }

    /** 命中总数（判断导出是否被截断）。 */
    public long count(String agentId, String toolId,
                      LocalDateTime from, LocalDateTime to, String reversibility) {
        return invocationRepository.count(
                InvocationSpecifications.withFilters(agentId, toolId, from, to, reversibility));
    }
}
