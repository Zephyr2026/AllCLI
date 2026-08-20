package com.allcli.gateway.catalog.event;

/**
 * 目录变更事件（事务提交后发布）：publish/gray/deprecate 后由 MCP 侧监听，
 * 增量刷新 tools/list（NFR-MAINT-01 热更新）。
 */
public record ToolCatalogChangedEvent(String toolId, String action, String version) {

    public static final String ACTION_PUBLISHED = "PUBLISHED";

    public static final String ACTION_GRAY = "GRAY";

    public static final String ACTION_DEPRECATED = "DEPRECATED";
}
