package com.allcli.gateway.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 工具方法：SHA256 digest（canonical JSON 输入）、业务 ID 生成。 */
public final class GatewayUtil {

    /** 共享 ObjectMapper：按 key 字母序输出，保证 digest 稳定。 */
    public static final ObjectMapper SORTED_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private GatewayUtil() {
    }

    /** 对任意对象做 canonical JSON 后求 SHA256 hex（digest 输入：对象本身的规范 JSON）。 */
    public static String digest(Object obj) {
        try {
            byte[] bytes = SORTED_MAPPER.writeValueAsBytes(obj);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(bytes);
            return toHex(d);
        } catch (Exception e) {
            throw new IllegalStateException("digest 计算失败", e);
        }
    }

    /** 生成业务 ID：前缀 + 时间基 + 随机。 */
    public static String id(String prefix) {
        return prefix + "_" + Long.toHexString(System.currentTimeMillis())
                + Long.toHexString((long) (Math.random() * 0xFFFF));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
