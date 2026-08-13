package com.allcli.gateway.credential.envelope;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * EnvelopeCipher 加解密往返单测（验证国密 SM4 软实现可用）。
 */
class EnvelopeCipherTest {

    private static final String KEK_HEX = "00112233445566778899aabbccddeeff";
    private final EnvelopeCipher cipher = new EnvelopeCipher();

    @Test
    void encryptDecryptRoundTrip() {
        String plaintext = "AllCLI-very-secret-token-2026";
        String ref = cipher.encrypt(plaintext, KEK_HEX);

        assertThat(ref).startsWith("allcli-vault://dek/");
        assertThat(ref).contains("#");
        assertThat(ref).doesNotContain(plaintext); // 明文不出现在密文引用里

        String decrypted = cipher.decrypt(ref, KEK_HEX);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void differentInvocationsProduceDifferentCipher() {
        // 每次随机 DEK + 随机 IV，同一明文应产生不同密文
        String p = "same-secret";
        String r1 = cipher.encrypt(p, KEK_HEX);
        String r2 = cipher.encrypt(p, KEK_HEX);
        assertThat(r1).isNotEqualTo(r2);
        assertThat(cipher.decrypt(r1, KEK_HEX)).isEqualTo(p);
        assertThat(cipher.decrypt(r2, KEK_HEX)).isEqualTo(p);
    }

    @Test
    void nonEnvelopeRefTreatedAsPlaintext() {
        // 兼容旧数据：非 allcli-vault:// 格式直接当明文
        assertThat(cipher.decrypt("raw-token", KEK_HEX)).isEqualTo("raw-token");
    }

    @Test
    void unicodeRoundTrip() {
        String p = "凭证密码-中文-🔑";
        String ref = cipher.encrypt(p, KEK_HEX);
        assertThat(cipher.decrypt(ref, KEK_HEX)).isEqualTo(p);
    }
}
