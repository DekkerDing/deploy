package io.github.dekkerding.deploy.delivery;

import org.springframework.stereotype.Component;

import java.util.List;

/** 交付提供者注册表：按通道（target_env.reach）路由，多实现并存。 */
@Component
public class DeliveryProviderRegistry {

    private final List<DeliveryProvider> providers;

    public DeliveryProviderRegistry(List<DeliveryProvider> providers) {
        this.providers = providers;
    }

    public DeliveryProvider resolve(String reach) {
        for (DeliveryProvider p : providers) {
            if (p.supports(reach)) {
                return p;
            }
        }
        throw new IllegalArgumentException("没有支持该通道的交付提供者: " + reach);
    }
}
