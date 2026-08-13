package com.allcli.engine.persistence;

import com.allcli.engine.cli.ConvertCommand.PersistHandler;
import com.allcli.engine.model.ToolDefinition;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 写库实现：把转化出的工具按 {@code tool_id} upsert 进 {@code sys_tool_definition}。
 *
 * <p>{@link Primary} 使其在与 {@code NoopPersistHandler} 并存时被 {@link com.allcli.engine.cli.ConvertCommand} 注入。
 */
@Service
@Primary
public class PersistService implements PersistHandler {

    private static final Logger log = LoggerFactory.getLogger(PersistService.class);

    private final ToolDefinitionRepository repository;

    public PersistService(ToolDefinitionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void persist(List<ToolDefinition> tools) {
        int inserted = 0;
        int updated = 0;
        for (ToolDefinition tool : tools) {
            ToolDefinition existing = repository.findByToolId(tool.getToolId()).orElse(null);
            if (existing != null) {
                tool.setId(existing.getId()); // 保留主键，触发 update
                updated++;
            } else {
                inserted++;
            }
            repository.save(tool);
        }
        log.info("写库完成：{} 个工具（新增 {}，更新 {}）", tools.size(), inserted, updated);
    }
}
