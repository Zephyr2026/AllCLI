package com.allcli.gateway.core.stages;

import com.allcli.gateway.api.dto.GatewayException;
import com.allcli.gateway.core.InvokeContext;
import com.allcli.gateway.model.ToolDefinition;
import org.springframework.stereotype.Component;

/**
 * Step 5：分级决策。据 tool.sideEffect/reversibility 决定处置方式。
 *
 * <table>
 *   <tr><th>sideEffect</th><th>reversibility</th><th>处置</th></tr>
 *   <tr><td>readonly</td><td>*</td><td>直放</td></tr>
 *   <tr><td>write</td><td>reversible</td><td>HITL（可 owner 关闭）</td></tr>
 *   <tr><td>write</td><td>irreversible</td><td>HITL + 二次确认</td></tr>
 *   <tr><td>write</td><td>未标注/readonly</td><td>MISSING_REVERSIBILITY（兜底拦截）</td></tr>
 * </table>
 */
@Component
public class ClassifyDecider {

    public enum Decision {
        DIRECT,             // 只读直放
        HITL,               // 写 + reversible
        HITL_CONFIRM        // 写 + irreversible（需二次确认）
    }

    public Decision decide(InvokeContext ctx) {
        ToolDefinition tool = ctx.getTool();
        String side = tool.getSideEffect();
        String rev = tool.getReversibility();

        if ("readonly".equals(side)) {
            return Decision.DIRECT;
        }
        if ("write".equals(side)) {
            ctx.setHitlRequired(true);
            if ("irreversible".equals(rev)) {
                return Decision.HITL_CONFIRM;
            }
            if ("reversible".equals(rev)) {
                return Decision.HITL;
            }
            // write 但 reversibility 未正确标注
            throw GatewayException.of("MISSING_REVERSIBILITY", "validation",
                    "写工具未正确标注可逆性: " + tool.getToolId(), false);
        }
        // 未知 sideEffect，保守按直放只读（不应出现）
        return Decision.DIRECT;
    }
}
