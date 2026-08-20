package com.allcli.gateway.persistence;

import com.allcli.gateway.model.ToolVersion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolVersionRepository extends JpaRepository<ToolVersion, Long> {

    boolean existsByToolIdAndVersion(String toolId, String version);

    Optional<ToolVersion> findByToolIdAndVersion(String toolId, String version);
}
