package io.github.dekkerding.deploy.example;

import io.github.dekkerding.deploy.build.BuildContext;
import io.github.dekkerding.deploy.build.BuildExecutor;
import io.github.dekkerding.deploy.build.BuildResult;
import io.github.dekkerding.deploy.build.BuildType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 示例插件执行器（plugin-system design D7）：SCRIPT 构建类型的最小实现。
 *
 * 约定：源码目录下放 <b>build.script</b>（每行一条命令，按顺序执行，工作目录=源码目录），
 * 构建产物放入 <b>dist/</b> 子目录，全部文件作为制品入库。
 *
 * 作为插件开发参照：零第三方依赖、无 Spring 依赖（插件实例非 Spring Bean，design D5）。
 */
public class ScriptBuildExecutor implements BuildExecutor {

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("windows");

    @Override
    public boolean supports(BuildType type) {
        return type == BuildType.SCRIPT;
    }

    @Override
    public BuildResult execute(BuildContext ctx) {
        Path source = ctx.getSourcePath();
        Path script = source.resolve("build.script");
        if (!Files.isRegularFile(script)) {
            return BuildResult.fail(-1, "SCRIPT 构建缺少 build.script: " + script
                    + "（约定：每行一条命令，产物放 dist/ 子目录）");
        }
        List<String> commands;
        try {
            commands = Files.readAllLines(script, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return BuildResult.fail(-1, "build.script 读取失败: " + e.getMessage());
        }

        Path log = ctx.getLogFile();
        try {
            Files.createDirectories(log.getParent());
        } catch (IOException e) {
            return BuildResult.fail(-1, "构建日志目录无法创建: " + e.getMessage());
        }

        for (String raw : commands) {
            String cmd = raw.trim();
            if (cmd.isEmpty() || cmd.startsWith("#")) {
                continue;
            }
            List<String> wrapped = new ArrayList<>();
            if (WINDOWS) {
                wrapped.add("cmd.exe");
                wrapped.add("/c");
            } else {
                wrapped.add("sh");
                wrapped.add("-c");
            }
            wrapped.add(cmd);
            try {
                appendLog(log, "$ " + cmd + System.lineSeparator());
                Process p = new ProcessBuilder(wrapped)
                        .directory(source.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()))
                        .start();
                if (ctx.getTimeoutMillis() > 0
                        && !p.waitFor(ctx.getTimeoutMillis(), TimeUnit.MILLISECONDS)) {
                    p.destroyForcibly();
                    appendLog(log, "[script-plugin] 构建超时: " + cmd + System.lineSeparator());
                    return BuildResult.fail(-1, "构建超时(" + ctx.getTimeoutMillis() + "ms): " + cmd);
                }
                int exit = p.waitFor();
                if (exit != 0) {
                    return BuildResult.fail(exit, "命令执行失败(exit=" + exit + "): " + cmd
                            + "，日志: ." + log);
                }
            } catch (IOException e) {
                return BuildResult.fail(-1, "命令启动失败: " + cmd + " (" + e.getMessage() + ")");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return BuildResult.fail(-1, "构建被中断");
            }
        }

        List<Path> produced = discoverProduced(source.resolve("dist"));
        if (produced.isEmpty()) {
            return BuildResult.fail(-1, "SCRIPT 构建无产物：dist/ 目录为空或不存在（" + source
                    + "）");
        }
        try {
            appendLog(log, "[script-plugin] 构建完成，产物 " + produced.size() + " 个" + System.lineSeparator());
        } catch (IOException ignored) {
            // 日志失败不影响构建结果
        }
        return BuildResult.ok(0, produced);
    }

    private static List<Path> discoverProduced(Path dist) {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(dist)) {
            return files;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dist)) {
            for (Path p : ds) {
                if (Files.isRegularFile(p)) {
                    files.add(p);
                }
            }
        } catch (IOException ignored) {
            // 目录不可读按空产物处理
        }
        return files;
    }

    private static void appendLog(Path log, String s) throws IOException {
        Files.write(log, s.getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }
}
