package io.github.dekkerding.deploy.delivery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PathPolicyTest {

    private final PathPolicy policy = new PathPolicy();

    @Test
    void linux路径策略() {
        assertThat(policy.installDir("linux", "demo-app", "1.2.3")).isEqualTo("/opt/demo-app/1.2.3/");
        assertThat(policy.artifactTargetPath("linux", "demo-app", "1.2.3", "app.jar"))
                .isEqualTo("/opt/demo-app/1.2.3/app.jar");
    }

    @Test
    void windows路径策略() {
        assertThat(policy.installDir("windows", "demo-app", "1.2.3"))
                .isEqualTo("C:\\apps\\demo-app\\1.2.3\\");
        assertThat(policy.artifactTargetPath("windows", "demo-app", "1.2.3", "svc.exe"))
                .isEqualTo("C:\\apps\\demo-app\\1.2.3\\svc.exe");
    }

    @Test
    void 未列举系统走类Unix策略() {
        assertThat(policy.installDir("freebsd", "app", "1.0")).isEqualTo("/opt/app/1.0/");
    }

    @Test
    void 非法字符净化防路径逃逸() {
        // 分隔符替换为 _，不产生裸 ../ 或 ..\ 序列
        assertThat(policy.installDir("linux", "../../etc", "1.0")).isEqualTo("/opt/.._.._etc/1.0/");
        assertThat(policy.installDir("windows", "..\\..\\cmd", "1.0"))
                .isEqualTo("C:\\apps\\.._.._cmd\\1.0\\");
        // Windows 保留字符替换为 -
        assertThat(policy.sanitize("a/b\\c:d*e?f\"g<h>i|j")).isEqualTo("a_b_c-d-e-f-g-h-i-j");
        assertThat(policy.sanitize("  ")).isEqualTo("unknown");
    }
}
