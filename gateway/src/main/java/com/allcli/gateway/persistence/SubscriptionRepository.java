package com.allcli.gateway.persistence;

import com.allcli.gateway.model.Subscription;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByAgentIdAndToolId(String agentId, String toolId);

    /** 目录：按 subId 定位（审批用）。 */
    Optional<Subscription> findBySubId(String subId);

    /** 管理面：按状态分页列表（待审批工作台等）。 */
    Page<Subscription> findByStatus(String status, Pageable pageable);
}
