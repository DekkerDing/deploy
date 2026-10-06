package io.github.dekkerding.deploy.build;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuildExecutorRegistryTest {

    /** 假实现 A：只支持 MAVEN */
    static class MavenStubExecutor implements BuildExecutor {
        @Override
        public boolean supports(BuildType type) {
            return type == BuildType.MAVEN;
        }

        @Override
        public BuildResult execute(BuildContext ctx) {
            return BuildResult.ok(0, Collections.singletonList(Paths.get("target/app.jar")));
        }
    }

    /** 假实现 B：GRADLE 与 NPM 都支持（验证一个执行器可覆盖多类型） */
    static class PolyglotStubExecutor implements BuildExecutor {
        @Override
        public boolean supports(BuildType type) {
            return type == BuildType.GRADLE || type == BuildType.NPM;
        }

        @Override
        public BuildResult execute(BuildContext ctx) {
            return BuildResult.fail(1, "stub failure");
        }
    }

    @Test
    void 多实现注册后按类型路由到正确执行器() {
        BuildExecutorRegistry registry = new BuildExecutorRegistry(
                Arrays.asList(new MavenStubExecutor(), new PolyglotStubExecutor()));

        assertThat(registry.resolve(BuildType.MAVEN)).isInstanceOf(MavenStubExecutor.class);
        assertThat(registry.resolve(BuildType.GRADLE)).isInstanceOf(PolyglotStubExecutor.class);
        assertThat(registry.resolve(BuildType.NPM)).isInstanceOf(PolyglotStubExecutor.class);
    }

    @Test
    void 进程执行器声明支持FLUTTER类型() {
        // 任务 4.1：FLUTTER 由 ProcessBuildExecutor 承接（design D5）
        ProcessBuildExecutor executor = new ProcessBuildExecutor();
        assertThat(executor.supports(BuildType.FLUTTER)).isTrue();
    }

    @Test
    void 无执行器支持时抛出明确异常() {
        BuildExecutorRegistry registry = new BuildExecutorRegistry(
                Collections.singletonList(new MavenStubExecutor()));

        assertThatThrownBy(() -> registry.resolve(BuildType.NPM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NPM");
    }

    @Test
    void BuildResult工厂方法语义正确() {
        BuildResult ok = BuildResult.ok(0, Arrays.asList(Paths.get("a.jar"), null));
        assertThat(ok.isSuccess()).isTrue();
        assertThat(ok.getProducedFiles()).hasSize(2);

        BuildResult fail = BuildResult.fail(137, "killed");
        assertThat(fail.isSuccess()).isFalse();
        assertThat(fail.getExitCode()).isEqualTo(137);
        assertThat(fail.getProducedFiles()).isEmpty();
    }
}
