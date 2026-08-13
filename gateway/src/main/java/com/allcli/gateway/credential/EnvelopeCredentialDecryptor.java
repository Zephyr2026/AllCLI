package com.allcli.gateway.credential;

import com.allcli.gateway.credential.envelope.EnvelopeCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 信封加密版凭证解密：用 KEK（SM4）解 secret_ref → 明文，注入下游 Header。
 *
 * <p>{@link Primary} 使其在与 {@link PlaintextCredentialDecryptor} 并存时被注入 UpstreamInvoker。
 * KEK 从配置 {@code allcli.credential.kek-hex} 读取（一期开发用固定值；生产换行内 KMS）。
 */
@Component
@Primary
public class EnvelopeCredentialDecryptor implements CredentialDecryptor {

    private final EnvelopeCipher cipher;
    private final String kekHex;

    public EnvelopeCredentialDecryptor(EnvelopeCipher cipher,
                                       @Value("${allcli.credential.kek-hex}") String kekHex) {
        this.cipher = cipher;
        this.kekHex = kekHex;
    }

    @Override
    public String decrypt(String secretRef) {
        return cipher.decrypt(secretRef, kekHex);
    }

    /** 供凭证录入侧加密明文为密文引用（一期 demo 用）。 */
    public String encrypt(String plaintext) {
        return cipher.encrypt(plaintext, kekHex);
    }
}
