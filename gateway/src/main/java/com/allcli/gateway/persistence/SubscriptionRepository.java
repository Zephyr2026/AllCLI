package com.allcli.gateway.persistence;

import com.allcli.gateway.model.Subscription;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByAgentIdAndToolId(String agentId, String toolId);

    /** 目录：按 subId 定位（审批用）。 */
    Optional<Subscription> findBySubId(String subId);
}
