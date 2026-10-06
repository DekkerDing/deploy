package io.github.dekkerding.deploy.plugin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 任务 1.2：兼容判定规则（相等/次版本低放行/主版本不符拒绝/畸形拒绝）。 */
class ExtensionApiVersionTest {

    @Test
    void 版本相等放行() {
        assertThat(ExtensionApiVersion.compatible("1.0")).isTrue();
        assertThat(ExtensionApiVersion.compatible("1.0", "1.0")).isTrue();
    }

    @Test
    void 插件次版本低于平台放行() {
        // 平台向后兼容新能力：旧插件（声明 1.0）跑在 1.1 平台上可用
        assertThat(ExtensionApiVersion.compatible("1.0", "1.1")).isTrue();
        assertThat(ExtensionApiVersion.compatible("1.2", "1.3")).isTrue();
    }

    @Test
    void 主版本不符拒绝() {
        assertThat(ExtensionApiVersion.compatible("1.0", "2.0")).isFalse();
        assertThat(ExtensionApiVersion.compatible("2.1", "1.9")).isFalse();
    }

    @Test
    void 插件次版本高于平台拒绝() {
        // 插件依赖了平台尚未提供的次版本能力
        assertThat(ExtensionApiVersion.compatible("1.2", "1.1")).isFalse();
    }

    @Test
    void 畸形版本拒绝() {
        assertThat(ExtensionApiVersion.compatible(null)).isFalse();
        assertThat(ExtensionApiVersion.compatible("1")).isFalse();
        assertThat(ExtensionApiVersion.compatible("1.0.0")).isFalse();
        assertThat(ExtensionApiVersion.compatible("a.b")).isFalse();
    }
}
