package com.allcli.engine.parser;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.AuthorizationValue;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * OpenAPI 3 解析封装（对标原 Go 的 kin-openapi 薄封装）。
 *
 * <p>关键 {@link ParseOptions}：
 * <ul>
 *   <li>{@code resolve=true} —— 解析外部/相对 $ref（多文件 spec 必需）。</li>
 *   <li>{@code resolveFully=true} —— 把内部 {@code #/components/...} $ref 内联进解析树，
 *       使 {@link io.swagger.v3.oas.models.media.Schema#getProperties()} 直接返回内联属性，
 *       {@link SchemaConverter} 无需自写 $ref 解析器。</li>
 *   <li>{@code inferSchemaType=false} —— 关闭类型推断，由 {@link SchemaConverter} 自己实现"缺省 type→string"。</li>
 * </ul>
 *
 * <p>该类为纯解析，不含 Spring 依赖，单测可直接 new 使用。
 */
public final class OpenApiLoader {

    private OpenApiLoader() {
    }

    /** 从本地文件或 URL 字符串加载并解析 OpenAPI 文档。 */
    public static OpenAPI load(String input) {
        SwaggerParseResult result;
        if (isUrl(input)) {
            result = new OpenAPIV3Parser().readLocation(input, auth(), parseOptions());
        } else {
            result = readContents(readFile(input));
        }
        return checked(result);
    }

    /**
     * 解析 OpenAPI 文档内容字符串（M4 网关导入 API 用：sourceContent 或抓取后的 sourceUrl 均走此入口）。
     * 与 {@link #load(String)} 共用同一份 ParseOptions 与错误处理。
     */
    public static OpenAPI parse(String content) {
        return checked(readContents(content));
    }

    private static ParseOptions parseOptions() {
        ParseOptions opts = new ParseOptions();
        opts.setResolve(true);
        opts.setResolveFully(true);
        opts.setInferSchemaType(false);
        return opts;
    }

    private static SwaggerParseResult readContents(String content) {
        return new OpenAPIV3Parser().readContents(content, auth(), parseOptions());
    }

    private static OpenAPI checked(SwaggerParseResult result) {
        if (result.getMessages() != null && !result.getMessages().isEmpty()) {
            // 解析告警不致命（如未识别扩展），仅记日志；openAPI 仍可能有效。
            System.err.println("[OpenApiLoader] parse warnings: " + result.getMessages());
        }
        OpenAPI openAPI = result.getOpenAPI();
        if (openAPI == null || openAPI.getPaths() == null) {
            throw new IllegalArgumentException("无法解析 OpenAPI 文档: " + result.getMessages());
        }
        return openAPI;
    }

    private static boolean isUrl(String input) {
        return input != null
                && (input.startsWith("http://") || input.startsWith("https://"));
    }

    private static String readFile(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (Exception e) {
            throw new IllegalArgumentException("读取 OpenAPI 文件失败: " + path, e);
        }
    }

    private static List<AuthorizationValue> auth() {
        return Collections.emptyList();
    }
}
