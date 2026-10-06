package io.github.dekkerding.deploy.delivery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * WinSW 适配器（任务 6.5）：XML 配置生成 + 服务安装/启动命令。
 * WinSW 可执行由平台随制品下发到目标机（每服务一份 exe+xml，exe 名即服务标识来源）。
 */
@Component
public class WinSwAdapter implements ServiceManager {

    /** 平台侧 WinSW 可执行位置（vendor 目录，gitignore）。 */
    @Value("${deploy.winsw.exe-path:./vendor/winsw/WinSW-x64.exe}")
    private String winswExePath;

    @Override
    public boolean supportsOs(String os) {
        return "windows".equalsIgnoreCase(os);
    }

    /** 服务 id：项目名净化（WinSW 同时作为 exe/xml 基名）。 */
    public String serviceId(DeliveryContext ctx) {
        return PathPolicy.sanitize(ctx.getProject().getName());
    }

    @Override
    public String generateServiceDefinition(DeliveryContext ctx) {
        String installDir = withTrailingSeparator(ctx.getRemoteInstallDir());
        String jar = ctx.getArtifact().getFileName();
        String javaExe = System.getProperty("java.home") + "\\bin\\java.exe";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<service>\n"
                + "  <id>" + serviceId(ctx) + "</id>\n"
                + "  <name>" + serviceId(ctx) + " (" + ctx.getRelease().getVersion() + ")</name>\n"
                + "  <description>deploy-platform managed: "
                + ctx.getProject().getName() + " " + ctx.getRelease().getVersion() + "</description>\n"
                + "  <executable>" + javaExe + "</executable>\n"
                + "  <arguments>-jar \"" + installDir + jar + "\"</arguments>\n"
                + "  <workingdirectory>" + installDir + "</workingdirectory>\n"
                + "  <logpath>" + installDir + "logs</logpath>\n"
                + "  <log mode=\"roll\"></log>\n"
                + "  <onfailure action=\"restart\" delay=\"5 sec\"/>\n"
                + "  <startmode>Automatic</startmode>\n"
                + "</service>\n";
    }

    /** 目录串保证以 \ 结尾（防御上游传无尾分隔符路径）。 */
    static String withTrailingSeparator(String dir) {
        if (dir == null || dir.isEmpty()) {
            return ".\\";
        }
        return dir.endsWith("\\") || dir.endsWith("/") ? dir : dir + "\\";
    }

    @Override
    public String serviceDefinitionPath(DeliveryContext ctx) {
        return Paths.get(ctx.getRemoteInstallDir(), serviceId(ctx) + ".xml").toString();
    }

    /** 目标机上 WinSW exe 的落点（与 xml 同目录同名基名）。 */
    public String targetWinswExePath(DeliveryContext ctx) {
        return Paths.get(ctx.getRemoteInstallDir(), serviceId(ctx) + ".exe").toString();
    }

    /** 平台侧 WinSW 可执行绝对路径（上传源）。 */
    public Path platformWinswExe() {
        Path p = Paths.get(winswExePath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(p)) {
            throw new IllegalStateException("WinSW 可执行缺失: " + p
                    + "（放置于 vendor/winsw/WinSW-x64.exe）");
        }
        return p;
    }

    @Override
    public List<String> activateCommands(DeliveryContext ctx) {
        String exe = targetWinswExePath(ctx);
        return Arrays.asList(
                "\"" + exe + "\" install",
                "\"" + exe + "\" start");
    }

    @Override
    public String statusCommand(DeliveryContext ctx) {
        return "sc query " + serviceId(ctx);
    }

    /** 回滚/下线用命令。 */
    public List<String> deactivateCommands(DeliveryContext ctx) {
        String exe = targetWinswExePath(ctx);
        return Arrays.asList(
                "\"" + exe + "\" stop",
                "\"" + exe + "\" uninstall");
    }
}
