package io.github.dekkerding.deploy.build;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactPlatformDetectorTest {

    @Test
    void 纯JAR与WAR自动PORTABLE且平台字段为空() {
        for (String name : new String[]{"app.jar", "hello-jar-1.0.0.jar", "web.war", "UPPER.JAR"}) {
            ArtifactPlatformDetector.Descriptor d = ArtifactPlatformDetector.detect(name);
            assertThat(d.portable).as(name).isTrue();
            assertThat(d.os).as(name).isNull();
            assertThat(d.arch).as(name).isNull();
            assertThat(d.libc).as(name).isNull();
        }
    }

    @Test
    void 原生制品绑定构建宿主平台() {
        // 本机是 Windows x86_64 开发机
        ArtifactPlatformDetector.Descriptor d = ArtifactPlatformDetector.detect("agent.exe");
        assertThat(d.portable).isFalse();
        assertThat(d.os).isEqualTo("windows");
        assertThat(d.arch).isEqualTo("x86_64");
        assertThat(d.libc).isEqualTo("msvc");
    }

    @Test
    void APK绑定android_arm64且libc留空通配() {
        // design D5：FLUTTER 构建产物按 android/arm64 入库；libc=null 在路由 exactMatch 中视为通配
        ArtifactPlatformDetector.Descriptor d = ArtifactPlatformDetector.detect("app-release.apk");
        assertThat(d.portable).isFalse();
        assertThat(d.os).isEqualTo("android");
        assertThat(d.arch).isEqualTo("arm64");
        assertThat(d.libc).isNull();
    }

    @Test
    void 未知扩展名保守按平台绑定处理() {
        ArtifactPlatformDetector.Descriptor d = ArtifactPlatformDetector.detect("bundle.bin");
        assertThat(d.portable).isFalse();
        assertThat(d.os).isNotBlank();
        assertThat(d.arch).isNotBlank();
    }

    @Test
    void 宿主平台归一化() {
        // 本机运行断言（win/amd64 环境下）
        assertThat(ArtifactPlatformDetector.hostOs()).isEqualTo("windows");
        assertThat(ArtifactPlatformDetector.hostArch()).isEqualTo("x86_64");
        assertThat(ArtifactPlatformDetector.hostLibc()).isEqualTo("msvc");
    }
}
