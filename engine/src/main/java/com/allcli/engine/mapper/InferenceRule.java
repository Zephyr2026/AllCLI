package com.allcli.engine.mapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 副作用 / 可逆性 / HITL 推断（对标原 Go 版 InferenceRule）。
 *
 * <p>纯函数、无 Spring 依赖。推断规则：
 * <table>
 *   <tr><th>method</th><th>sideEffect</th><th>reversibility</th><th>hitl</th></tr>
 *   <tr><td>GET</td><td>readonly</td><td>readonly</td><td>不挂（null）</td></tr>
 *   <tr><td>POST/PUT/PATCH</td><td>write</td><td>reversible</td><td>挂 owner/600s</td></tr>
 *   <tr><td>DELETE</td><td>write</td><td>irreversible</td><td>挂 owner/600s</td></tr>
 * </table>
 */
public final class InferenceRule {

    /** 写操作的 HITL 超时（秒）。 */
    public static final int HITL_TIMEOUT_SEC = 600;

    private static final Set<String> READ_METHODS = Set.of("GET");
    private static final Set<String> IRREVERSIBLE_METHODS = Set.of("DELETE");

    private InferenceRule() {
    }

    /** 推断单个 operation 的三类属性。method 为大写 HTTP 动词。 */
    public static Inference infer(String method, String owner) {
        String m = method == null ? "" : method.toUpperCase();
        boolean readonly = READ_METHODS.contains(m);

        String sideEffect = readonly ? "readonly" : "write";
        String reversibility = readonly
                ? "readonly"
                : (IRREVERSIBLE_METHODS.contains(m) ? "irreversible" : "reversible");
        Map<String, Object> hitl = readonly ? null : hitl(owner);

        return new Inference(sideEffect, reversibility, hitl);
    }

    private static Map<String, Object> hitl(String owner) {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("enabled", true);
        h.put("approverType", "owner");
        h.put("approverRef", owner);
        h.put("timeoutSec", HITL_TIMEOUT_SEC);
        return h;
    }

    /** 推断结果。 */
    @Getter
    @AllArgsConstructor
    public static final class Inference {
        private final String sideEffect;
        private final String reversibility;
        /** 只读操作为 null。 */
        private final Map<String, Object> hitl;
    }
}
