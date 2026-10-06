package io.github.dekkerding.deploy.delivery;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * systemd 适配器（任务 6.4）：unit 模板生成 + daemon-reload/restart 命令。
 * 真实 systemd 验证待 linux 目标接入后（任务说明明确允许），本任务验证模板正确性。
 */
@Component
public class SystemdAdapter implements ServiceManager {

    @Override
    public boolean supportsOs(String os) {
        return "linux".equalsIgnoreCase(os) || "freebsd".equalsIgnoreCase(os)
                || "openbsd".equalsIgnoreCase(os) || "netbsd".equalsIgnoreCase(os);
    }

    @Override
    public String generateServiceDefinition(DeliveryContext ctx) {
        String jar = ctx.getArtifact().getFileName();
        String installDir = ctx.getRemoteInstallDir();
        String desc = ctx.getProject().getName() + " " + ctx.getRelease().getVersion();
        String serviceName = ctx.getProject().getName();
        return "[Unit]\n"
                + "Description=" + desc + "\n"
                + "After=network.target\n"
                + "\n"
                + "[Service]\n"
                + "Type=simple\n"
                + "WorkingDirectory=" + installDir + "\n"
                + "ExecStart=/usr/bin/java -jar " + installDir + jar + "\n"
                + "Restart=on-failure\n"
                + "RestartSec=5\n"
                + "\n"
                + "[Install]\n"
                + "WantedBy=multi-user.target\n"
                + "# managed-by: deploy-platform (service=" + serviceName + ")\n";
    }

    @Override
    public String serviceDefinitionPath(DeliveryContext ctx) {
        return "/etc/systemd/system/" + ctx.getProject().getName() + ".service";
    }

    @Override
    public List<String> activateCommands(DeliveryContext ctx) {
        String svc = ctx.getProject().getName() + ".service";
        return Arrays.asList(
                "systemctl daemon-reload",
                "systemctl enable " + svc,
                "systemctl restart " + svc);
    }

    @Override
    public List<String> deactivateCommands(DeliveryContext ctx) {
        // 只 stop 不 disable：回滚流程随后安装旧版本 unit 并 enable+restart
        return Arrays.asList("systemctl stop " + ctx.getProject().getName() + ".service");
    }

    @Override
    public String statusCommand(DeliveryContext ctx) {
        return "systemctl is-active " + ctx.getProject().getName() + ".service";
    }
}
