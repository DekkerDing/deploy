package io.github.dekkerding.deploy.delivery;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * systemd 适配器（任务 6.4）：unit 模板生成 + daemon-reload/restart 命令。
 * 真实 systemd 验证待 linux 目标接入后（任务说明明确允许），本任务验证模板正确性。
 *
 * 多实例（specs/instance-scaling / design D4）：服务名 app@N（N=实例编号）。
 * 因每实例端口不同（basePort+N-1）而 systemd 模板单元无法按 %i 做端口算术，
 * 采用每实例独立 unit 实体文件 project@N.service（命名保留 @N 语义），
 * ExecStart 注入该实例专属端口。
 */
@Component
public class SystemdAdapter implements ServiceManager {

    @Override
    public boolean supportsOs(String os) {
        return "linux".equalsIgnoreCase(os) || "freebsd".equalsIgnoreCase(os)
                || "openbsd".equalsIgnoreCase(os) || "netbsd".equalsIgnoreCase(os);
    }

    /** 服务单元名：单实例 project.service；多实例 project@N.service。 */
    public String unitName(DeliveryContext ctx) {
        String name = ctx.getProject().getName();
        return ctx.getInstanceSeq() != null
                ? name + "@" + ctx.getInstanceSeq() + ".service"
                : name + ".service";
    }

    @Override
    public String generateServiceDefinition(DeliveryContext ctx) {
        String jar = ctx.getArtifact().getFileName();
        String installDir = ctx.getRemoteInstallDir();
        String desc = ctx.getProject().getName() + " " + ctx.getRelease().getVersion();
        String serviceName = ctx.getProject().getName();
        // 实例端口注入（Spring Boot 惯例 --server.port；多实例端口错开的关键）
        String portArgs = ctx.getInstancePort() != null
                ? " --server.port=" + ctx.getInstancePort() : "";
        return "[Unit]\n"
                + "Description=" + desc + (ctx.getInstanceSeq() != null
                        ? " (instance " + ctx.getInstanceSeq() + ")" : "") + "\n"
                + "After=network.target\n"
                + "\n"
                + "[Service]\n"
                + "Type=simple\n"
                + "WorkingDirectory=" + installDir + "\n"
                + "ExecStart=/usr/bin/java -jar " + installDir + jar + portArgs + "\n"
                + "Restart=on-failure\n"
                + "RestartSec=5\n"
                + "\n"
                + "[Install]\n"
                + "WantedBy=multi-user.target\n"
                + "# managed-by: deploy-platform (service=" + serviceName + ")\n";
    }

    @Override
    public String serviceDefinitionPath(DeliveryContext ctx) {
        return "/etc/systemd/system/" + unitName(ctx);
    }

    @Override
    public List<String> activateCommands(DeliveryContext ctx) {
        String svc = unitName(ctx);
        List<String> cmds = new ArrayList<>(Arrays.asList(
                "systemctl daemon-reload",
                "systemctl enable " + svc));
        // 多实例扩容逐个拉起：已运行的实例 1 不重启（restart 会打断存量），新实例用 start
        cmds.add(ctx.getInstanceSeq() != null ? "systemctl start " + svc : "systemctl restart " + svc);
        return cmds;
    }

    @Override
    public List<String> deactivateCommands(DeliveryContext ctx) {
        // 只 stop 不 disable：回滚流程随后安装旧版本 unit 并 enable+restart；
        // 缩容裁尾则由编排层 removeByEnvAndSeq 删实例行
        return Arrays.asList("systemctl stop " + unitName(ctx));
    }

    @Override
    public String statusCommand(DeliveryContext ctx) {
        return "systemctl is-active " + unitName(ctx);
    }
}
