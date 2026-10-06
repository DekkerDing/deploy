package io.github.dekkerding.deploy.plugin;

import io.github.dekkerding.deploy.build.BuildExecutor;
import io.github.dekkerding.deploy.build.BuildExecutorRegistry;
import io.github.dekkerding.deploy.build.ProcessBuildExecutor;
import io.github.dekkerding.deploy.delivery.DeliveryContext;
import io.github.dekkerding.deploy.delivery.DeliveryProvider;
import io.github.dekkerding.deploy.delivery.DeliveryProviderRegistry;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务 1.3：注册表编程式注册入口——插件实例（非 Spring Bean）注册后立即可枚举与路由。
 */
class RegistryProgrammaticRegisterTest {

    @Test
    void 编程注册内建执行器后类型经枚举可见并可路由() {
        BuildExecutorRegistry registry = new BuildExecutorRegistry(Collections.emptyList());
        assertThat(registry.supportedTypeNames()).isEmpty();

        registry.register(new ProcessBuildExecutor()); // 内建实现模拟插件路径注册
        assertThat(registry.supportedTypeNames())
                .containsExactlyInAnyOrder("MAVEN", "GRADLE", "NPM", "FLUTTER");
        assertThat(registry.resolve(io.github.dekkerding.deploy.build.BuildType.GRADLE))
                .isInstanceOf(ProcessBuildExecutor.class);
    }

    @Test
    void 编程注册提供者后通道经枚举可见并可路由() {
        DeliveryProviderRegistry registry = new DeliveryProviderRegistry(Collections.emptyList());
        assertThat(registry.supportedReaches()).isEmpty();

        registry.register(new LocalStubProvider());
        assertThat(registry.supportedReaches()).containsExactly("LOCAL");
        assertThat(registry.resolve("LOCAL")).isInstanceOf(LocalStubProvider.class);
    }

    /** 模拟插件 DeliveryProvider：自述 LOCAL 通道。 */
    static class LocalStubProvider implements DeliveryProvider {
        @Override
        public boolean supports(String reach) {
            return "LOCAL".equalsIgnoreCase(reach);
        }

        @Override
        public String deliver(DeliveryContext ctx) {
            return "stub";
        }

        @Override
        public String rollback(DeliveryContext rollbackTo, DeliveryContext current) {
            return "stub";
        }

        @Override
        public Set<String> declaredReaches() {
            return Collections.singleton("LOCAL");
        }
    }
}
