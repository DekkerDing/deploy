package io.github.dekkerding.deploy.delivery;

import io.github.dekkerding.deploy.domain.entity.DeploymentEntity;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryProviderRegistryTest {

    static class SshStubProvider implements DeliveryProvider {
        @Override
        public boolean supports(String reach) {
            return "SSH".equalsIgnoreCase(reach);
        }

        @Override
        public String deliver(DeliveryContext ctx) {
            return "ssh-stub";
        }

        @Override
        public String rollback(DeliveryContext ctx, DeploymentEntity lastSuccess) {
            return "ssh-rollback-stub";
        }
    }

    static class LocalStubProvider implements DeliveryProvider {
        @Override
        public boolean supports(String reach) {
            return "LOCAL".equalsIgnoreCase(reach);
        }

        @Override
        public String deliver(DeliveryContext ctx) {
            return "local-stub";
        }

        @Override
        public String rollback(DeliveryContext ctx, DeploymentEntity lastSuccess) {
            return "local-rollback-stub";
        }
    }

    @Test
    void 多实现注册按通道路由() {
        DeliveryProviderRegistry registry = new DeliveryProviderRegistry(
                Arrays.asList(new SshStubProvider(), new LocalStubProvider()));
        assertThat(registry.resolve("SSH")).isInstanceOf(SshStubProvider.class);
        assertThat(registry.resolve("LOCAL")).isInstanceOf(LocalStubProvider.class);
        assertThat(registry.resolve("ssh")).isInstanceOf(SshStubProvider.class); // 大小写不敏感
    }

    @Test
    void 无实现通道抛出明确异常() {
        DeliveryProviderRegistry registry = new DeliveryProviderRegistry(
                Collections.singletonList(new SshStubProvider()));
        assertThatThrownBy(() -> registry.resolve("WINRM"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WINRM");
    }

    @Test
    void SPI可执行deliver与rollback() {
        DeliveryProviderRegistry registry = new DeliveryProviderRegistry(
                Collections.singletonList(new LocalStubProvider()));
        DeliveryProvider p = registry.resolve("LOCAL");
        assertThat(p.deliver(null)).isEqualTo("local-stub");
        assertThat(p.rollback(null, null)).isEqualTo("local-rollback-stub");
    }
}
