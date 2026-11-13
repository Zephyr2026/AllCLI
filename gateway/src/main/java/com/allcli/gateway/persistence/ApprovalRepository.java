package com.allcli.gateway.persistence;

import com.allcli.gateway.model.Approval;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {

    Optional<Approval> findByApprovalId(String approvalId);

    Optional<Approval> findByInvocationId(String invocationId);

    /** 管理面：HITL 待办列表（decision=pending）。 */
    Page<Approval> findByDecision(String decision, Pageable pageable);
}
