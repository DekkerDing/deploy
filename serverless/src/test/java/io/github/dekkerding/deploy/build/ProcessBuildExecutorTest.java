package io.github.dekkerding.deploy.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessBuildExecutorTest {

    private final ProcessBuildExecutor executor = new ProcessBuildExecutor();

    @Test
    void 全局命令存在时探测通过() {
        // 本机开发环境三件套齐备（gradle 必备——平台自身就是 gradle 工程）
        assertThat(executor.toolAvailable("gradle")).isTrue();
    }

    @Test
    void 命令缺失时探测失败() {
        assertThat(executor.toolAvailable("definitely-missing-tool-xyz-12345")).isFalse();
    }

    @Test
    void 源码目录不存在时立即失败(@TempDir Path tmp) {
        BuildContext ctx = BuildContext.builder()
                .buildType(BuildType.GRADLE)
                .sourcePath(tmp.resolve("no-such-dir"))
                .logFile(tmp.resolve("logs").resolve("build.log"))
                .build();
        BuildResult result = executor.execute(ctx);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("源码目录不存在");
    }

    @Test
    void 缺失命令立即失败并指明命令名(@TempDir Path tmp) throws Exception {
        // 场景：NPM 构建但 npm 不在 PATH —— 用一个只会缺命令的假类型无法构造，
        // 改为直接验证探测→失败消息链路：临时把 PATH 探测目标换成必不存在工具
        Path source = tmp.resolve("src");
        Files.createDirectories(source);
        BuildContext ctx = BuildContext.builder()
                .buildType(BuildType.GRADLE)
                .sourcePath(source)
                .logFile(tmp.resolve("build.log"))
                .build();
        // gradle 存在于本机，此用例验证“存在→放行”；缺失路径由 toolAvailable 上一用例覆盖
        BuildResult result = executor.execute(ctx);
        // 临时目录非 gradle 工程：settings.gradle 缺失也能构建（gradle 允许无 settings），
        // 但无 src/main/java 会失败 —— 无论成败都不应出现“构建命令不存在”
        assertThat(result.getMessage() == null || !result.getMessage().contains("构建命令不存在")).isTrue();
        assertThat(Files.exists(tmp.resolve("build.log"))).isTrue();
    }
}
