package com.allcli.engine.mapper;

/**
 * operationId 缺省时，由 {@code METHOD_path} 生成合法工具标识。
 *
 * <p>规则（对标原 Go 版 sanitize）：把 path 中非 {@code [a-zA-Z0-9]} 字符替换为 {@code _}，
 * 拼成 {@code method_path} 形式，去除首尾下划线。method 取小写。
 * 例：{@code DELETE /pets/{petId}} → {@code delete_ppets_petId}（大括号转下划线）。
 */
public final class ToolIdSanitizer {

    private ToolIdSanitizer() {
    }

    public static String sanitize(String method, String path) {
        String raw = (method == null ? "" : method.toLowerCase()) + "_" + (path == null ? "" : path);
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            sb.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        // 去首尾下划线，合并连续下划线
        String result = sb.toString().replaceAll("_+", "_").replaceAll("^_", "").replaceAll("_$", "");
        return result.isEmpty() ? "tool" : result;
    }
}
