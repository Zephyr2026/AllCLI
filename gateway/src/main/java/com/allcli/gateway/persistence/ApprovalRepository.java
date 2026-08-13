package com.allcli.gateway.persistence;

import com.allcli.gateway.model.Approval;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {

    Optional<Approval> findByApprovalId(String approvalId);

    Optional<Approval> findByInvocationId(String invocationId);
}
