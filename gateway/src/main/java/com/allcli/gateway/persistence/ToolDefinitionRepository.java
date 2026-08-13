package com.allcli.gateway.persistence;

import com.allcli.gateway.model.ToolDefinition;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolDefinitionRepository extends JpaRepository<ToolDefinition, Long> {

    Optional<ToolDefinition> findByToolId(String toolId);

    Optional<ToolDefinition> findByToolIdAndVersion(String toolId, String version);
}
