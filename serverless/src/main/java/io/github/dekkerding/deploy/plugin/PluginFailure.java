package io.github.dekkerding.deploy.plugin;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 失败插件条目（清单 API 数据源，specs/plugin-loading：标识或文件名 + 原因）。 */
@Data
@AllArgsConstructor
public class PluginFailure {

    /** 插件 id（已解析到 manifest 时）或 jar 文件名。 */
    private String identifier;

    /** 失败原因（读取失败/缺属性/版本不兼容/实例化异常等）。 */
    private String reason;
}
