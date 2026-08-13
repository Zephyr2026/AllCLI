package com.allcli.engine.persistence;

import com.allcli.engine.model.ToolDefinition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * sys_tool_definition 的 JPA Repository。
 *
 * <p>upsert 语义由 {@link PersistService} 按 tool_id 判存亡实现。
 */
public interface ToolDefinitionRepository extends JpaRepository<ToolDefinition, Long> {

    Optional<ToolDefinition> findByToolId(String toolId);

    List<ToolDefinition> findBySystemRef(String systemRef);
}
