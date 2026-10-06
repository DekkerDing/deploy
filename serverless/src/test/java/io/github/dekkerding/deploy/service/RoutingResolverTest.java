package io.github.dekkerding.deploy.service;

import io.github.dekkerding.deploy.domain.entity.ArtifactEntity;
import io.github.dekkerding.deploy.domain.entity.TargetEnvEntity;
import io.github.dekkerding.deploy.service.RoutingResolver.Decision;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingResolverTest {

    private final RoutingResolver resolver = new RoutingResolver();

    private static ArtifactEntity artifact(String name, String os, String arch, String libc, boolean portable) {
        ArtifactEntity a = new ArtifactEntity();
        a.setFileName(name);
        a.setPlatformOs(os);
        a.setPlatformArch(arch);
        a.setPlatformLibc(libc);
        a.setPortable(portable);
        return a;
    }

    private static TargetEnvEntity env(String os, String arch, String libc, String probeStatus) {
        TargetEnvEntity e = new TargetEnvEntity();
        e.setName("t-" + os + arch);
        e.setOs(os);
        e.setArch(arch);
        e.setLibc(libc);
        e.setRuntimeType("NATIVE");
        e.setReach("SSH");
        e.setProbeStatus(probeStatus);
        return e;
    }

    @Test
    void 精确匹配命中绑定制品() {
        Decision d = resolver.resolve(
                Collections.singletonList(artifact("svc.exe", "windows", "amd64", "msvc", false)),
                env("windows", "amd64", "msvc", "KNOWN"));
        assertThat(d.matched).isTrue();
        assertThat(d.artifact.getFileName()).isEqualTo("svc.exe");
        assertThat(d.reason).contains("精确匹配");
    }

    @Test
    void APK制品对android目标精确匹配且libc通配() {
        // 任务 4.3：APK 绑定 android/arm64/libc=null；目标 libc 空（未填）或显式 bionic 均命中
        Decision d1 = resolver.resolve(
                Collections.singletonList(artifact("app-release.apk", "android", "arm64", null, false)),
                env("android", "arm64", null, "KNOWN"));
        assertThat(d1.matched).isTrue();
        assertThat(d1.artifact.getFileName()).isEqualTo("app-release.apk");
        assertThat(d1.reason).contains("精确匹配");

        Decision d2 = resolver.resolve(
                Collections.singletonList(artifact("app-release.apk", "android", "arm64", null, false)),
                env("android", "arm64", "bionic", "KNOWN"));
        assertThat(d2.matched).isTrue();
    }

    @Test
    void APK制品不匹配非android目标() {
        Decision d = resolver.resolve(
                Collections.singletonList(artifact("app-release.apk", "android", "arm64", null, false)),
                env("windows", "amd64", "msvc", "KNOWN"));
        assertThat(d.matched).isFalse();
        assertThat(d.reason).contains("无匹配制品");
    }

    @Test
    void PORTABLE制品回退路由到任意平台() {
        Decision d = resolver.resolve(
                Collections.singletonList(artifact("app.jar", null, null, null, true)),
                env("linux", "arm64", "musl", "KNOWN"));
        assertThat(d.matched).isTrue();
        assertThat(d.reason).contains("PORTABLE 回退");
    }

    @Test
    void 混合制品优先精确匹配而非回退() {
        Decision d = resolver.resolve(
                Arrays.asList(
                        artifact("app.jar", null, null, null, true),
                        artifact("svc.bin", "linux", "arm64", "musl", false)),
                env("linux", "arm64", "musl", "KNOWN"));
        assertThat(d.matched).isTrue();
        assertThat(d.artifact.getFileName()).isEqualTo("svc.bin");
    }

    @Test
    void 无匹配时拒绝并附制品平台清单() {
        Decision d = resolver.resolve(
                Arrays.asList(
                        artifact("svc.exe", "windows", "amd64", "msvc", false),
                        artifact("svc2.so", "linux", "amd64", "glibc", false)),
                env("linux", "arm64", "musl", "KNOWN"));
        assertThat(d.matched).isFalse();
        assertThat(d.reason).contains("linux/arm64/musl");
        assertThat(d.platformList).containsExactly(
                "svc.exe[windows/amd64/msvc]", "svc2.so[linux/amd64/glibc]");
    }

    @Test
    void 探针UNKNOWN一律阻止交付() {
        Decision d = resolver.resolve(
                Collections.singletonList(artifact("app.jar", null, null, null, true)),
                env("linux", "amd64", "glibc", "UNKNOWN"));
        assertThat(d.matched).isFalse();
        assertThat(d.reason).contains("UNKNOWN");
    }

    @Test
    void 空制品列表拒绝() {
        Decision d = resolver.resolve(Collections.<ArtifactEntity>emptyList(),
                env("linux", "amd64", "glibc", "KNOWN"));
        assertThat(d.matched).isFalse();
        assertThat(d.reason).contains("没有可交付制品");
    }
}
