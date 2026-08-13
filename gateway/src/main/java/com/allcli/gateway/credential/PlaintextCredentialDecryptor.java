package com.allcli.gateway.credential;

import org.springframework.stereotype.Component;

/**
 * 占位实现：secret_ref 直接当明文返回（仅任务17 直放链路联调用，不安全）。
 *
 * <p>任务20 引入信封加密的 {@code EnvelopeCredentialDecryptor}（@Primary）后将覆盖此 Bean。
 */
@Component
public class PlaintextCredentialDecryptor implements CredentialDecryptor {

    @Override
    public String decrypt(String secretRef) {
        return secretRef;
    }
}
