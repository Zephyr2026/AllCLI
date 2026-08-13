package com.allcli.gateway.core.stages;

import com.allcli.gateway.core.InvokeContext;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Step 8：结果归一化 + 脱敏。
 *
 * <p>递归遍历结果，对已知敏感 key（password/token/secret 等）置 {@code ***}。
 * M2 简单实现；精细脱敏规则（按工具配置）留后续。
 */
@Component
public class ResultNormalizer {

    private static final Set<String> SENSITIVE = new HashSet<>(List.of(
            "password", "passwd", "token", "secret", "apikey", "api_key", "credential"));

    public Object normalize(Object result) {
        return redact(result);
    }

    @SuppressWarnings("unchecked")
    private Object redact(Object node) {
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (SENSITIVE.contains(e.getKey().toLowerCase())) {
                    out.put(e.getKey(), "***");
                } else {
                    out.put(e.getKey(), redact(e.getValue()));
                }
            }
            return out;
        }
        if (node instanceof List) {
            List<Object> list = (List<Object>) node;
            return list.stream().map(this::redact).toList();
        }
        return node;
    }
}
