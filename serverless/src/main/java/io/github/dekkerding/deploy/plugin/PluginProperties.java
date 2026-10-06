package io.github.dekkerding.deploy.plugin;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 插件系统配置（design D3 / tasks 2.1）。 */
@Data
@Component
@ConfigurationProperties(prefix = "plugins")
public class PluginProperties {

    /** 插件目录（相对平台工作目录或绝对路径）；不存在时正常启动。 */
    private String dir = "./plugins";

    /** 禁用子目录名（目录内）：jar 移入后下次启动不再加载。 */
    private String disabledSubdir = "disabled";
}
