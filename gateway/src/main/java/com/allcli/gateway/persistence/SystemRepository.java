package com.allcli.gateway.persistence;

import com.allcli.gateway.model.System;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemRepository extends JpaRepository<System, Long> {

    Optional<System> findBySystemId(String systemId);

    boolean existsBySystemId(String systemId);
}
