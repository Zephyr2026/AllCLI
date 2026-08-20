package com.allcli.gateway.persistence;

import com.allcli.gateway.model.ToolDefinition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ToolDefinitionRepository
        extends JpaRepository<ToolDefinition, Long>, JpaSpecificationExecutor<ToolDefinition> {

    Optional<ToolDefinition> findByToolId(String toolId);

    Optional<ToolDefinition> findByToolIdAndVersion(String toolId, String version);

    /** 目录/MCP：按状态取工具（published/gray）。 */
    List<ToolDefinition> findByStatus(String status);
}
