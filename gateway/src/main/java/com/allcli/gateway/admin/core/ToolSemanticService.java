package com.allcli.gateway.admin.core;

import com.allcli.gateway.admin.api.dto.ToolSemanticUpdateRequest;
import com.allcli.gateway.admin.api.dto.ToolSemanticUpdateResponse;
import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.catalog.event.ToolCatalogChangedEvent;
import com.allcli.gateway.model.ToolDefinition;
import com.allcli.gateway.persistence.ToolDefinitionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工具语义编辑（FR-SEM-01~04，admin 面）：描述/权限/副作用/可逆分级/幂等键/HITL。
 *
 * <p>按状态分级的编辑策略（保护已发布语义与版本快照的可回溯性）：
 * <ul>
 *   <li>draft/reviewing：任意编辑直接生效（不在 MCP 中，不发事件）。</li>
 *   <li>published/gray 仅 description 变更 → 热生效，发 {@code ACTION_UPDATED}
 *       （MCP 刷新描述与 _meta）；治理字段（sideEffect/reversibility/hitl/
 *       requiredPermission/idempotencyKey）变更 → 自动回退 draft + 版本 patch 自增 +
 *       发 {@code ACTION_DEPRECATED}（MCP 即时下线）+ warning。重新发布会重新过
 *       MISSING_REVERSIBILITY 门禁，快照链保持可回溯。</li>
 *   <li>deprecated：拒绝编辑。</li>
 * </ul>
 *
 * <p>warnings 为 advisory（一期不强制）；致命校验（枚举非法、hitl.enabled 无审批人）抛
 * VALIDATION_ERROR。合并语义：非 null 覆盖、hitl 整体替换（无法显式清空字段，一期接受）。
 */
@Service
public class ToolSemanticService {

    private static final Set<String> SIDE_EFFECTS = Set.of("readonly", "write");
    private static final Set<String> REVERSIBILITIES = Set.of("readonly", "reversible", "irreversible");

    private final ToolDefinitionRepository toolRepo;
    private final ApplicationEventPublisher eventPublisher;

    public ToolSemanticService(ToolDefinitionRepository toolRepo,
                               ApplicationEventPublisher eventPublisher) {
        this.toolRepo = toolRepo;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ToolSemanticUpdateResponse apply(String toolId, ToolSemanticUpdateRequest req) {
        ToolDefinition tool = toolRepo.findByToolId(toolId)
                .orElseThrow(() -> GatewayException.of("TOOL_NOT_FOUND", "validation",
                        "工具不存在: " + toolId, false));
        if ("deprecated".equals(tool.getStatus())) {
            throw GatewayException.of("INVALID_STATUS_TRANSITION", "business",
                    "已下线工具不可编辑: " + toolId, false);
        }

        boolean governanceTouched = req.getSideEffect() != null || req.getReversibility() != null
                || req.getIdempotencyKey() != null || req.getRequiredPermission() != null
                || req.getHitl() != null;
        boolean live = "published".equals(tool.getStatus()) || "gray".equals(tool.getStatus());

        validate(req);
        List<String> warnings = new ArrayList<>();

        if (req.getDescription() != null) {
            tool.setDescription(req.getDescription());
        }
        if (req.getRequiredPermission() != null) {
            tool.setRequiredPermission(req.getRequiredPermission());
        }
        if (req.getSideEffect() != null) {
            tool.setSideEffect(req.getSideEffect());
        }
        if (req.getReversibility() != null) {
            tool.setReversibility(req.getReversibility());
        }
        if (req.getIdempotencyKey() != null) {
            tool.setIdempotencyKey(req.getIdempotencyKey());
        }
        if (req.getHitl() != null) {
            tool.setHitl(req.getHitl());
        }

        // 联动：只读工具不应再挂 HITL（自动清 + 告警）
        boolean hitlOn = tool.getHitl() != null && Boolean.TRUE.equals(tool.getHitl().get("enabled"));
        if ("readonly".equals(tool.getSideEffect()) && hitlOn) {
            tool.setHitl(null);
            hitlOn = false;
            warnings.add("HITL_CLEARED_FOR_READONLY");
        }

        // 合并后终值告警
        if ("write".equals(tool.getSideEffect())
                && !Set.of("reversible", "irreversible").contains(tool.getReversibility())) {
            warnings.add("WRITE_MISSING_REVERSIBILITY");
        }
        if ("write".equals(tool.getSideEffect()) && !hitlOn) {
            warnings.add("WRITE_WITHOUT_HITL");
        }
        if ("irreversible".equals(tool.getReversibility()) && !hitlOn) {
            warnings.add("IRREVERSIBLE_WITHOUT_HITL");
        }

        String action = null;
        if (live) {
            if (governanceTouched) {
                tool.setStatus("draft");
                warnings.add("TOOL_REVERTED_TO_DRAFT");
                if (!bumpPatchVersion(tool)) {
                    warnings.add("VERSION_NOT_AUTO_BUMPED");
                }
                action = ToolCatalogChangedEvent.ACTION_DEPRECATED;   // MCP 立即下线
            } else if (req.getDescription() != null) {
                action = ToolCatalogChangedEvent.ACTION_UPDATED;      // MCP 热刷新描述
            }
        }
        toolRepo.save(tool);
        if (action != null) {
            eventPublisher.publishEvent(new ToolCatalogChangedEvent(toolId, action, tool.getVersion()));
        }
        return ToolSemanticUpdateResponse.builder()
                .toolId(toolId)
                .version(tool.getVersion())
                .status(tool.getStatus())
                .warnings(warnings)
                .build();
    }

    private void validate(ToolSemanticUpdateRequest req) {
        if (req.getSideEffect() != null && !SIDE_EFFECTS.contains(req.getSideEffect())) {
            throw GatewayException.of("VALIDATION_ERROR", "validation",
                    "sideEffect 必须为 " + SIDE_EFFECTS, false);
        }
        if (req.getReversibility() != null && !REVERSIBILITIES.contains(req.getReversibility())) {
            throw GatewayException.of("VALIDATION_ERROR", "validation",
                    "reversibility 必须为 " + REVERSIBILITIES, false);
        }
        Map<String, Object> hitl = req.getHitl();
        if (hitl != null && Boolean.TRUE.equals(hitl.get("enabled"))) {
            Object approverRef = hitl.get("approverRef");
            if (approverRef == null || String.valueOf(approverRef).isBlank()) {
                throw GatewayException.of("VALIDATION_ERROR", "validation",
                        "hitl.enabled=true 时 approverRef 必填", false);
            }
            Object timeout = hitl.get("timeoutSec");
            if (timeout instanceof Number n && n.longValue() <= 0) {
                throw GatewayException.of("VALIDATION_ERROR", "validation",
                        "hitl.timeoutSec 必须 > 0", false);
            }
        }
    }

    /** d.d.d 版本 patch 自增（1.0.0→1.0.1）；不可解析返回 false（调用方给 warning）。 */
    private boolean bumpPatchVersion(ToolDefinition tool) {
        String[] parts = tool.getVersion() == null ? new String[0] : tool.getVersion().split("\\.");
        if (parts.length != 3) {
            return false;
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = Integer.parseInt(parts[2]);
            tool.setVersion(major + "." + minor + "." + (patch + 1));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
