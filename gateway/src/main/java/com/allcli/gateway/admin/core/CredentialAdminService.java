package com.allcli.gateway.admin.core;

import com.allcli.gateway.admin.api.dto.CredentialRegisterRequest;
import com.allcli.gateway.admin.api.dto.CredentialResponse;
import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.core.GatewayUtil;
import com.allcli.gateway.credential.EnvelopeCredentialDecryptor;
import com.allcli.gateway.model.Credential;
import com.allcli.gateway.persistence.CredentialRepository;
import com.allcli.gateway.persistence.SystemRepository;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 凭证注册（M4 控制面，FR-CONV-04）：明文 → SM4 信封加密 → secret_ref 落库。
 * 明文仅在本次请求内存中存在，永不入库、不回显、不写日志。
 */
@Service
public class CredentialAdminService {

    private static final Set<String> TYPES = Set.of("Vault", "KMS", "Opaque");

    private final CredentialRepository credentialRepository;
    private final SystemRepository systemRepository;
    private final EnvelopeCredentialDecryptor cipher;

    public CredentialAdminService(CredentialRepository credentialRepository,
                                  SystemRepository systemRepository,
                                  EnvelopeCredentialDecryptor cipher) {
        this.credentialRepository = credentialRepository;
        this.systemRepository = systemRepository;
        this.cipher = cipher;
    }

    public CredentialResponse register(CredentialRegisterRequest req) {
        String systemRef = requireText(req.getSystemRef(), "systemRef");
        String type = requireText(req.getType(), "type");
        String secret = requireText(req.getSecret(), "secret");
        if (!TYPES.contains(type)) {
            throw GatewayException.of("VALIDATION_ERROR", "validation",
                    "type 必须为 " + TYPES, false);
        }
        if (!systemRepository.existsBySystemId(systemRef)) {
            throw GatewayException.of("SYSTEM_NOT_FOUND", "business",
                    "系统未注册，请先 POST /api/v1/systems: " + systemRef, false);
        }
        String secretRef = cipher.encrypt(secret);
        Credential saved = credentialRepository.save(Credential.builder()
                .credId(GatewayUtil.id("cred"))
                .systemRef(systemRef)
                .type(type)
                .secretRef(secretRef)
                .rotatePolicy(req.getRotatePolicy())
                .status("active")
                .build());
        return CredentialResponse.builder()
                .credId(saved.getCredId())
                .systemRef(saved.getSystemRef())
                .type(saved.getType())
                .rotatePolicy(saved.getRotatePolicy())
                .status(saved.getStatus())
                .build();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw GatewayException.of("VALIDATION_ERROR", "validation", field + " 不能为空", false);
        }
        return value.trim();
    }
}
