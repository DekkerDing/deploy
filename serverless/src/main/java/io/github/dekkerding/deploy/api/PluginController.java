package io.github.dekkerding.deploy.api;

import io.github.dekkerding.deploy.build.BuildExecutorRegistry;
import io.github.dekkerding.deploy.delivery.DeliveryProviderRegistry;
import io.github.dekkerding.deploy.plugin.PluginDescriptor;
import io.github.dekkerding.deploy.plugin.PluginFailure;
import io.github.dekkerding.deploy.plugin.PluginManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 插件清单与能力枚举（specs/plugin-loading：清单查询 + 贡献的扩展点经既有 API 可见）。
 * GET /api/plugins：已加载（id/版本/贡献计数）与失败（标识+原因）插件。
 * GET /api/meta：当前可用构建类型与交付通道（含插件贡献）。
 */
@RestController
@RequiredArgsConstructor
public class PluginController {

    private final PluginManager pluginManager;
    private final BuildExecutorRegistry executorRegistry;
    private final DeliveryProviderRegistry providerRegistry;

    @GetMapping("/api/plugins")
    public Map<String, Object> plugins() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("loaded", pluginManager.getLoaded());
        view.put("failures", pluginManager.getFailures());
        return view;
    }

    @GetMapping("/api/meta")
    public Map<String, Object> meta() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("buildTypes", executorRegistry.supportedTypeNames());
        view.put("deliveryReaches", providerRegistry.supportedReaches());
        return view;
    }
}
