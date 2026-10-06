package io.github.dekkerding.deploy.probe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformProbeTest {

    private final PlatformProbe probe = new PlatformProbe();

    @ParameterizedTest(name = "uname \"{0}\" → {1}/{2}")
    @CsvSource({
            "Linux x86_64, linux, amd64",
            "Darwin arm64, darwin, arm64",
            "Darwin x86_64, darwin, amd64",
            "FreeBSD amd64, freebsd, amd64",
            "OpenBSD arm64, openbsd, arm64",
            "Linux aarch64, linux, arm64",
            "Linux armv7l, linux, armv7",
            "GNU/Linux x86_64, linux, amd64",
    })
    void uname解析矩阵(String input, String os, String arch) {
        PlatformProbe.ProbeResult r = probe.parseUname(input);
        assertThat(r.known).as(input).isTrue();
        assertThat(r.os).as(input).isEqualTo(os);
        assertThat(r.arch).as(input).isEqualTo(arch);
    }

    @Test
    void 无法识别的输出标记UNKNOWN() {
        assertThat(probe.parseUname("").known).isFalse();
        assertThat(probe.parseUname(null).known).isFalse();
        assertThat(probe.parseUname("WeirdOS").known).isFalse(); // 只有一个词
        assertThat(probe.parseUname("WeirdOS zzz").known).isFalse(); // 未知组合
        assertThat(probe.parseWindowsArch("RISC-V9999").known).isFalse();
    }

    @Test
    void Windows处理器架构变量矩阵() {
        assertThat(probe.parseWindowsArch("AMD64").arch).isEqualTo("amd64");
        assertThat(probe.parseWindowsArch("ARM64").arch).isEqualTo("arm64");
        assertThat(probe.parseWindowsArch("x86").arch).isEqualTo("386");
        PlatformProbe.ProbeResult r = probe.parseWindowsArch("AMD64");
        assertThat(r.os).isEqualTo("windows");
        assertThat(r.libc).isEqualTo("msvc");
        assertThat(r.known).isTrue();
    }

    @Test
    void ldd输出细化libc() {
        assertThat(probe.libcFromLdd("musl libc (x86_64)")).isEqualTo("musl");
        assertThat(probe.libcFromLdd("ldd (Ubuntu GLIBC 2.35-0ubuntu3) 2.35")).isEqualTo("glibc");
    }

    @Test
    void 本机真实探针结果正确() {
        // 开发机为 Windows x86_64（与 ArtifactPlatformDetector 宿主断言一致）
        PlatformProbe.ProbeResult r = probe.local();
        assertThat(r.known).isTrue();
        assertThat(r.os).isEqualTo("windows");
        assertThat(r.arch).isEqualTo("amd64");
    }
}
