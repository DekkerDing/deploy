package io.github.dekkerding.deploy.plugin;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/** 已加载插件条目（清单 API 数据源，specs/plugin-loading）。 */
@Data
@AllArgsConstructor
public class PluginDescriptor {

    /** manifest Plugin-Id。 */
    private String id;

    /** manifest Plugin-Version。 */
    private String version;

    /** jar 文件名（磁盘定位）。 */
    private String fileName;

    /** 贡献的扩展点名 → 注册实现数（如 BuildExecutor→1）。 */
    private Map<String, Integer> contributions;
}
