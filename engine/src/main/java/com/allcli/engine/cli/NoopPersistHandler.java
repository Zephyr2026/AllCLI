package com.allcli.engine.cli;

import com.allcli.engine.model.ToolDefinition;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link ConvertCommand.PersistHandler} 的默认 no-op 实现。
 *
 * <p>仅记录日志不写库；持久化模块可定义同接口的 {@code @Primary} Bean 覆盖之。
 */
@Component
public class NoopPersistHandler implements ConvertCommand.PersistHandler {

    private static final Logger log = LoggerFactory.getLogger(NoopPersistHandler.class);

    @Override
    public void persist(List<ToolDefinition> tools) {
        log.warn("--persist 已传入，但当前无持久化实现，跳过写库（{} 个工具）", tools.size());
    }
}
