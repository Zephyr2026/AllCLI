package com.allcli.engine.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 转化输出信封：{@code {tools:[...], count:N}}。
 *
 * <p>与原 Go 版 CLI 输出格式一致，便于对标。
 */
@Getter
@AllArgsConstructor
public class ConvertResult {

    private final List<ToolDefinition> tools;

    public int getCount() {
        return tools == null ? 0 : tools.size();
    }
}
