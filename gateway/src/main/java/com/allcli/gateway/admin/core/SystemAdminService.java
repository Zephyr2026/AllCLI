package com.allcli.gateway.admin.core;

import com.allcli.gateway.admin.api.dto.SystemRegisterRequest;
import com.allcli.gateway.admin.api.dto.SystemResponse;
import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.model.System;
import com.allcli.gateway.persistence.SystemRepository;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 存量系统注册（M4 控制面，FR-CONV 前置）：导入/凭证/工具均挂靠系统。
 * 一期无更新/下线接口（直改 DB），注册幂等由 uk_system_id 保证。
 */
@Service
public class SystemAdminService {

    private static final Set<String> AUTH_TYPES = Set.of("OAuth2", "ApiKey", "Cookie", "None");

    private final SystemRepository systemRepository;

    public SystemAdminService(SystemRepository systemRepository) {
        this.systemRepository = systemRepository;
    }

    public SystemResponse register(SystemRegisterRequest req) {
        String systemId = requireText(req.getSystemId(), "systemId");
        requireText(req.getName(), "name");
        requireText(req.getBaseUrl(), "baseUrl");
        requireText(req.getOwner(), "owner");
        String authType = requireText(req.getAuthType(), "authType");
        if (!AUTH_TYPES.contains(authType)) {
            throw GatewayException.of("VALIDATION_ERROR", "validation",
                    "authType 必须为 " + AUTH_TYPES, false);
        }
        if (systemRepository.existsBySystemId(systemId)) {
            throw GatewayException.of("SYSTEM_ALREADY_EXISTS", "business",
                    "系统已注册: " + systemId, false);
        }
        System saved = systemRepository.save(System.builder()
                .systemId(systemId)
                .name(req.getName().trim())
                .baseUrl(req.getBaseUrl().trim())
                .authType(authType)
                .owner(req.getOwner().trim())
                .status("active")
                .extra(Map.of())
                .build());
        return toResponse(saved);
    }

    static SystemResponse toResponse(System s) {
        return SystemResponse.builder()
                .systemId(s.getSystemId())
                .name(s.getName())
                .baseUrl(s.getBaseUrl())
                .authType(s.getAuthType())
                .owner(s.getOwner())
                .status(s.getStatus())
                .build();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw GatewayException.of("VALIDATION_ERROR", "validation", field + " 不能为空", false);
        }
        return value.trim();
    }
}
