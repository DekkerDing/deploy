package io.github.dekkerding.deploy.delivery;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 交付提供者注册表：按通道（target_env.reach）路由，多实现并存。 */
@Component
public class DeliveryProviderRegistry {

    private final List<DeliveryProvider> providers;

    public DeliveryProviderRegistry(List<DeliveryProvider> providers) {
        // 可变副本：插件加载后经 register() 编程式追加（design D5，非 Spring Bean 路径）
        this.providers = new ArrayList<>(providers);
    }

    public DeliveryProvider resolve(String reach) {
        for (DeliveryProvider p : providers) {
            if (p.supports(reach)) {
                return p;
            }
        }
        throw new IllegalArgumentException("没有支持该通道的交付提供者: " + reach);
    }

    /** 编程式注册（插件加载器调用）：追加提供者并立即可被 resolve 命中。 */
    public void register(DeliveryProvider provider) {
        providers.add(provider);
    }

    /** 当前有提供者承接的交付通道名集合（API 枚举数据源，插件贡献自然出现）。 */
    public Set<String> supportedReaches() {
        Set<String> reaches = new LinkedHashSet<>();
        for (DeliveryProvider p : providers) {
            // 无环境清单可枚举，由提供者自述支持通道（内建 SSH/LOCAL 等）
            for (String r : p.declaredReaches()) {
                reaches.add(r);
            }
        }
        return reaches;
    }
}
