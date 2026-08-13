package com.allcli.gateway.credential;

/**
 * 凭证解密：把 sys_credential.secret_ref（密文引用）解为明文，供注入下游 Header。
 *
 * <p>一期实现：明文直通（任务17 占位）；M2 后续（任务20）接信封加密 SM4。
 */
public interface CredentialDecryptor {

    /**
     * @param secretRef 密文引用（如 allcli-vault://... 或一期明文）
     * @return 凭证明文
     */
    String decrypt(String secretRef);
}
