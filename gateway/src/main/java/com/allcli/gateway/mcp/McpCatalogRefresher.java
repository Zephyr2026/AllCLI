package com.allcli.gateway.mcp;

import com.allcli.gateway.catalog.event.ToolCatalogChangedEvent;
import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.persistence.ToolDefinitionRepository;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * MCP 目录动态刷新（D12 同进程内闭环）：
 * publish/gray/deprecate 事务提交后 → McpSyncServer.addTool/removeTool 并广播
 * tools/list_changed，客户端无需重连（NFR-MAINT-01 热更新）。
 *
 * <p>单节点内存态——多实例部署时其他节点需重启或后续引入广播（M3 风险清单）。
 * {@link #resync()} 提供全量对账：DB published 集合为准，多退少补，测试种子/兜底用。
 */
@Component
public class McpCatalogRefresher {

    private static final Logger log = LoggerFactory.getLogger(McpCatalogRefresher.class);

    private final McpSyncServer server;
    private final McpToolRegistry registry;
    private final ToolDefinitionRepository toolRepo;

    /** 本节点已注册进 MCP server 的 toolId（含启动装载），resync 移除下线项的依据。 */
    private final Set<String> registered = ConcurrentHashMap.newKeySet();

    public McpCatalogRefresher(McpSyncServer server,
                               McpToolRegistry registry,
                               ToolDefinitionRepository toolRepo,
                               ObjectProvider<List<McpServerFeatures.SyncToolSpecification>> startupSpecs) {
        this.server = server;
        this.registry = registry;
        this.toolRepo = toolRepo;
        startupSpecs.ifAvailable(specs -> specs.forEach(s -> registered.add(s.tool().name())));
    }

    /** 目录变更 → 增量刷新（事务提交后）；失败仅记日志，不回滚目录本身。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCatalogChanged(ToolCatalogChangedEvent event) {
        try {
            switch (event.action()) {
                case ToolCatalogChangedEvent.ACTION_PUBLISHED,
                     ToolCatalogChangedEvent.ACTION_GRAY,
                     ToolCatalogChangedEvent.ACTION_UPDATED -> toolRepo.findByToolId(event.toolId())
                        .ifPresentOrElse(t -> server.addTool(registry.specificationOf(t)),
                                () -> log.warn("目录变更但工具不存在，忽略: {}", event.toolId()));
                case ToolCatalogChangedEvent.ACTION_DEPRECATED -> server.removeTool(event.toolId());
                default -> log.warn("未知目录变更动作，忽略: {}", event);
            }
            server.notifyToolsListChanged();
            log.info("MCP 目录已刷新: {} {}", event.toolId(), event.action());
        } catch (Exception e) {
            log.error("MCP 目录刷新失败（下次 resync/重启对账）: {} {}", event.toolId(), event.action(), e);
        }
    }

    /** 全量重同步：以 DB published 集合为准，多退少补（测试种子直写 repo 后调用；亦可作对账兜底）。 */
    public synchronized void resync() {
        List<ToolDefinition> published = toolRepo.findByStatus("published");
        Set<String> publishedIds = published.stream().map(ToolDefinition::getToolId).collect(java.util.stream.Collectors.toSet());

        for (Iterator<String> it = registered.iterator(); it.hasNext();) {
            String toolId = it.next();
            if (!publishedIds.contains(toolId)) {
                server.removeTool(toolId);
                it.remove();
            }
        }
        for (ToolDefinition tool : published) {
            server.addTool(registry.specificationOf(tool));
            registered.add(tool.getToolId());
        }
        server.notifyToolsListChanged();
        log.info("MCP 目录全量同步完成: published={} registered={}", publishedIds.size(), registered.size());
    }
}
