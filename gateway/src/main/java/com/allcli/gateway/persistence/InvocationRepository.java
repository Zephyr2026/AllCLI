package com.allcli.gateway.persistence;

import com.allcli.gateway.model.Invocation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvocationRepository extends JpaRepository<Invocation, Long> {

    Optional<Invocation> findByInvocationId(String invocationId);

    /** 幂等：按 agentId×toolId×idempotencyKey（M2 用 input_digest 近似查找近期成功记录） */
    Optional<Invocation> findFirstByAgentIdAndToolIdAndInputDigestAndStatusOrderByIdDesc(
            String agentId, String toolId, String inputDigest, String status);
}
