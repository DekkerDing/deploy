package io.github.dekkerding.deploy.build;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 子进程构建执行器（design D5）：mvn / gradle / npm。
 *
 * Windows 下 mvn/npm 是 .cmd shim、gradlew 是 .bat，ProcessBuilder 无法直接执行，
 * 统一包一层 cmd.exe /c；超时后 destroyForcibly 强杀进程树中的直接子进程。
 */
@Component
public class ProcessBuildExecutor implements BuildExecutor {

    private static final Set<BuildType> SUPPORTED = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(BuildType.MAVEN, BuildType.GRADLE, BuildType.NPM)));

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    /** 需要安装在全局 PATH 的工具名（wrapper 脚本不算）。 */
    private static final Set<String> GLOBAL_TOOLS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("mvn", "gradle", "npm")));

    /** 探测全局命令是否可用（where / command -v），5 秒超时视为不可用。 */
    boolean toolAvailable(String tool) {
        List<String> probe = WINDOWS
                ? Arrays.asList("cmd.exe", "/c", "where " + tool)
                : Arrays.asList("sh", "-c", "command -v " + tool);
        try {
            Process p = new ProcessBuilder(probe).redirectErrorStream(true).start();
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean supports(BuildType type) {
        return SUPPORTED.contains(type);
    }

    @Override
    public BuildResult execute(BuildContext ctx) {
        Path source = ctx.getSourcePath();
        if (source == null || !Files.isDirectory(source)) {
            return BuildResult.fail(-1, "源码目录不存在: " + source);
        }
        Path log = ctx.getLogFile();
        try {
            Files.createDirectories(log.getParent());
            if (!Files.exists(log)) {
                Files.createFile(log);
            }
        } catch (IOException e) {
            return BuildResult.fail(-1, "构建日志文件无法创建: " + log + " (" + e.getMessage() + ")");
        }

        List<List<String>> steps = commandsFor(ctx.getBuildType(), source);
        // 命令缺失时立即失败并指明缺失命令（任务 3.2）：只探测全局工具，项目内 wrapper 必然存在
        for (List<String> step : steps) {
            String tool = step.get(0);
            if (GLOBAL_TOOLS.contains(tool) && !toolAvailable(tool)) {
                return BuildResult.fail(-1, "构建命令不存在: " + tool + "（请安装并加入 PATH）");
            }
        }
        long start = System.currentTimeMillis();
        try {
            for (List<String> cmd : steps) {
                appendLog(log, "$ " + join(cmd) + System.lineSeparator());
                ProcessBuilder pb = new ProcessBuilder(wrapForWindows(cmd));
                pb.directory(source.toFile());
                pb.redirectErrorStream(true);
                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
                Process p = pb.start();

                if (ctx.getTimeoutMillis() > 0) {
                    if (!p.waitFor(ctx.getTimeoutMillis(), TimeUnit.MILLISECONDS)) {
                        p.destroyForcibly();
                        appendLog(log, "[platform] 构建超时(" + ctx.getTimeoutMillis() + "ms)，已强制终止: "
                                + join(cmd) + System.lineSeparator());
                        return BuildResult.fail(-1,
                                "构建超时(" + ctx.getTimeoutMillis() + "ms): " + join(cmd)
                                        + tailSummary(log));
                    }
                } else {
                    p.waitFor();
                }
                int exit = p.exitValue();
                if (exit != 0) {
                    return BuildResult.fail(exit, "命令执行失败(exit=" + exit + "): " + join(cmd)
                            + "，日志: " + log + tailSummary(log));
                }
            }
            List<Path> produced = discoverProduced(ctx.getBuildType(), source);
            appendLog(log, "[platform] 构建完成，耗时 " + (System.currentTimeMillis() - start)
                    + "ms，产物 " + produced.size() + " 个" + System.lineSeparator());
            return BuildResult.ok(0, produced);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return BuildResult.fail(-1, "构建被中断");
        } catch (IOException e) {
            return BuildResult.fail(-1, "构建进程启动失败: " + e.getMessage()
                    + "（请确认 mvn/gradle/npm 已安装并在 PATH 中）");
        }
    }

    /** 每种类型的命令步骤（NPM 需要两步：install + build）。优先使用项目自带 wrapper。 */
    private List<List<String>> commandsFor(BuildType type, Path source) {
        switch (type) {
            case MAVEN:
                return Collections.singletonList(Arrays.asList(
                        pickWrapper(source, "mvnw.cmd", "mvnw", "mvn"),
                        "clean", "package", "-DskipTests", "-B"));
            case GRADLE:
                return Collections.singletonList(Arrays.asList(
                        pickWrapper(source, "gradlew.bat", "gradlew", "gradle"),
                        "clean", "build", "-x", "test"));
            case NPM:
                return Arrays.asList(
                        Arrays.asList("npm", "install"),
                        Arrays.asList("npm", "run", "build"));
            default:
                throw new IllegalArgumentException("不支持的构建类型: " + type);
        }
    }

    private String pickWrapper(Path source, String... candidates) {
        for (String c : candidates) {
            if (Files.exists(source.resolve(c))) {
                return c;
            }
        }
        return candidates[candidates.length - 1];
    }

    private List<String> wrapForWindows(List<String> cmd) {
        if (!WINDOWS) {
            return cmd;
        }
        List<String> wrapped = new ArrayList<>();
        wrapped.add("cmd.exe");
        wrapped.add("/c");
        wrapped.addAll(cmd);
        return wrapped;
    }

    /** 构建产物发现：maven → target/*.jar；gradle → build/libs/*.jar；npm → dist 目录。 */
    private List<Path> discoverProduced(BuildType type, Path source) {
        Path dir;
        String glob;
        if (type == BuildType.MAVEN) {
            dir = source.resolve("target");
            glob = "*.jar";
        } else if (type == BuildType.GRADLE) {
            dir = source.resolve(Paths.get("build", "libs"));
            glob = "*.jar";
        } else {
            Path dist = source.resolve("dist");
            return Files.isDirectory(dist) ? Collections.singletonList(dist) : Collections.<Path>emptyList();
        }
        if (!Files.isDirectory(dir)) {
            return Collections.emptyList();
        }
        List<Path> jars = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, glob)) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                if (name.endsWith("-sources.jar") || name.endsWith("-javadoc.jar")) {
                    continue;
                }
                jars.add(p);
            }
        } catch (IOException ignored) {
            // 目录不可读时按无产物处理，由上层（制品入库）报告缺失
        }
        Collections.sort(jars);
        return jars;
    }

    private void appendLog(Path log, String line) {
        try {
            Files.write(log, line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // 日志追加失败不影响构建本身
        }
    }

    /** 失败消息附带的日志尾摘要（任务 3.5）：末尾至多 20 行 / 1500 字符，便于在不打开完整日志时定位原因。 */
    static String tailSummary(Path log) {
        try {
            byte[] all = Files.readAllBytes(log);
            if (all.length == 0) {
                return "\n[日志尾摘要: 无输出]";
            }
            String text = new String(all, StandardCharsets.UTF_8);
            String[] lines = text.split("\r?\n");
            int from = Math.max(0, lines.length - 20);
            StringBuilder sb = new StringBuilder("\n[日志尾摘要]\n");
            for (int i = from; i < lines.length; i++) {
                sb.append(lines[i]).append('\n');
            }
            String s = sb.toString();
            return s.length() > 1500 ? s.substring(s.length() - 1500) : s;
        } catch (IOException e) {
            return "\n[日志尾摘要读取失败: " + e.getMessage() + "]";
        }
    }

    private String join(List<String> cmd) {
        StringBuilder sb = new StringBuilder();
        for (String c : cmd) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
