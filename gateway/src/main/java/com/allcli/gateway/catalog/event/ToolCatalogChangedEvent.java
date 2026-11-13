package com.allcli.gateway.catalog.event;

/**
 * 目录变更事件（事务提交后发布）：publish/gray/deprecate/语义编辑后由 MCP 侧监听，
 * 增量刷新 tools/list（NFR-MAINT-01 热更新）。
 */
public record ToolCatalogChangedEvent(String toolId, String action, String version) {

    public static final String ACTION_PUBLISHED = "PUBLISHED";

    public static final String ACTION_GRAY = "GRAY";

    public static final String ACTION_DEPRECATED = "DEPRECATED";

    /** published 工具仅 description 热修改（治理字段未动）→ 重挂 MCP 刷新描述与 _meta */
    public static final String ACTION_UPDATED = "UPDATED";
}
