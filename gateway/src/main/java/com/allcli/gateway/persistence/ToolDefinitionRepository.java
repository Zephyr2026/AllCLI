package com.allcli.gateway.persistence;

import com.allcli.gateway.model.ToolDefinition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ToolDefinitionRepository
        extends JpaRepository<ToolDefinition, Long>, JpaSpecificationExecutor<ToolDefinition> {

    Optional<ToolDefinition> findByToolId(String toolId);

    Optional<ToolDefinition> findByToolIdAndVersion(String toolId, String version);

    /** 目录/MCP：按状态取工具（published/gray）。 */
    List<ToolDefinition> findByStatus(String status);

    /** 看板（FR-OPS-01）：工具按状态计数，返回 [String status, Long count]。 */
    @Query("select t.status, count(t) from ToolDefinition t group by t.status")
    List<Object[]> countGroupByStatus();
}
