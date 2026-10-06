package io.github.dekkerding.deploy.delivery;

import io.github.dekkerding.deploy.domain.entity.TargetEnvEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * SSH 交付提供者（specs/ssh-jar-delivery）：把各组件组装成完整交付动作。
 *
 * deliver  = 上传制品 → 写服务定义 →（windows 附带 WinSW exe）→ 激活服务 → TCP 健康检查
 * rollback = 停用当前版本（失败仅告警，服务可能已宕机）→ 按旧版本完整重装 → 健康检查
 *
 * 旧版本目录由 PathPolicy 每版本独立目录保证仍在目标机上；重装走幂等上传。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SshDeliveryProvider implements DeliveryProvider {

    private final SshChannel channel;
    private final PathPolicy pathPolicy;
    private final List<ServiceManager> serviceManagers;
    private final WinSwAdapter winSwAdapter;
    private final HealthChecker healthChecker;

    @Override
    public boolean supports(String reach) {
        return "SSH".equalsIgnoreCase(reach);
    }

    @Override
    public java.util.Set<String> declaredReaches() {
        return java.util.Collections.singleton("SSH");
    }

    @Override
    public String deliver(DeliveryContext ctx) {
        fillInstallDir(ctx);
        try (SSHClient client = connect(ctx.getTargetEnv())) {
            installAndActivate(client, ctx);
            return healthGate(ctx, "交付 " + ctx.getProject().getName() + " "
                    + ctx.getRelease().getVersion() + " 到 " + ctx.getRemoteInstallDir());
        } catch (IOException e) {
            throw new DeliveryException("SSH 通道异常: " + e.getMessage(), e);
        }
    }

    @Override
    public String rollback(DeliveryContext rollbackTo, DeliveryContext current) {
        fillInstallDir(rollbackTo);
        fillInstallDir(current);
        ServiceManager sm = resolveServiceManager(rollbackTo.getTargetEnv().getOs());
        try (SSHClient client = connect(rollbackTo.getTargetEnv())) {
            // 停用当前版本：命令失败仅告警（回滚诱因常是服务已死，不能因停不掉而拒绝回滚）
            for (String cmd : sm.deactivateCommands(current)) {
                try {
                    execOrThrow(client, cmd, "停用当前版本 " + current.getRelease().getVersion());
                } catch (DeliveryException e) {
                    log.warn("回滚前停用命令未成功（继续回滚）: {}", e.getMessage());
                }
            }
            installAndActivate(client, rollbackTo);
            return healthGate(rollbackTo, "回滚到 " + rollbackTo.getProject().getName() + " "
                    + rollbackTo.getRelease().getVersion() + "（" + ctxVersion(current) + " → 停用）");
        } catch (IOException e) {
            throw new DeliveryException("SSH 通道异常: " + e.getMessage(), e);
        }
    }

    /** 停止并注销指定上下文的服务（specs/instance-scaling 缩容裁尾；命令失败仅告警不阻断）。 */
    public void deactivateService(DeliveryContext ctx) {
        fillInstallDir(ctx);
        ServiceManager sm = resolveServiceManager(ctx.getTargetEnv().getOs());
        try (SSHClient client = connect(ctx.getTargetEnv())) {
            for (String cmd : sm.deactivateCommands(ctx)) {
                try {
                    execOrThrow(client, cmd, "停用实例 seq=" + ctx.getInstanceSeq());
                } catch (DeliveryException e) {
                    log.warn("缩容停用命令未成功（继续，目标机可能有残留需对账）: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new DeliveryException("SSH 通道异常: " + e.getMessage(), e);
        }
    }

    /** 上传制品与服务定义并执行激活命令（deliver 与 rollback 复用）。 */
    private void installAndActivate(SSHClient client, DeliveryContext ctx) throws IOException {
        TargetEnvEntity env = ctx.getTargetEnv();
        ServiceManager sm = resolveServiceManager(env.getOs());

        // 幂等清理（升级/回滚场景）：同 id 服务可能已存在（WinSW install 遇 1073），
        // 且 Windows 上运行中服务的文件被进程锁定——必须先停用再上传，否则
        // SFTP 覆盖运行中 jar 会以 SSH_FX_FAILURE 失败。停用失败仅告警：
        // 服务可能本就未安装或已宕机（全新目录交付不受影响）。
        for (String cmd : sm.deactivateCommands(ctx)) {
            try {
                execOrThrow(client, cmd, "停用旧服务");
            } catch (DeliveryException e) {
                log.warn("停用旧服务未成功（继续安装）: {}", e.getMessage());
            }
        }

        // 制品上传（扩缩容跳过：共享版本目录中制品已存在，且运行中实例锁定 jar 时
        // Windows SFTP 覆盖必失败——specs/instance-scaling 复用首次交付的制品）
        if (!Boolean.TRUE.equals(ctx.getSkipArtifactUpload())) {
            channel.upload(client, ctx.getLocalArtifactPath(),
                    ctx.getRemoteInstallDir() + ctx.getArtifact().getFileName());
        }

        // 服务定义内容先落本地临时文件再上传（SshChannel 只提供文件上传）
        Path defTmp = Files.createTempFile("deploy-svc-def-", ".tmp");
        try {
            Files.write(defTmp, sm.generateServiceDefinition(ctx).getBytes(StandardCharsets.UTF_8));
            channel.upload(client, defTmp.toString(), sm.serviceDefinitionPath(ctx));
        } finally {
            Files.deleteIfExists(defTmp);
        }

        if (sm instanceof WinSwAdapter) {
            channel.upload(client, winSwAdapter.platformWinswExe().toString(),
                    winSwAdapter.targetWinswExePath(ctx));
        }

        for (String cmd : sm.activateCommands(ctx)) {
            execOrThrow(client, cmd, "激活服务");
        }
    }

    /** 健康检查门（specs：探活成功方记成功；未配置端口则跳过并注明）。
     * 多实例交付按实例端口探活（specs/instance-scaling 实例级健康检查）。 */
    private String healthGate(DeliveryContext ctx, String summary) {
        TargetEnvEntity env = ctx.getTargetEnv();
        Integer probePort = ctx.getInstancePort() != null ? ctx.getInstancePort() : env.getHealthCheckPort();
        if (probePort == null) {
            return summary + "；未配置 healthCheckPort，跳过探活";
        }
        HealthChecker.HealthResult r = healthChecker.check(env.getHost(), probePort);
        if (!r.healthy) {
            throw new DeliveryException(summary + "；" + r.message);
        }
        return summary + "；" + r.message;
    }

    private void execOrThrow(SSHClient client, String cmd, String stage) throws IOException {
        SshChannel.Execution exec = channel.exec(client, cmd);
        if (!exec.isSuccess()) {
            throw new DeliveryException(stage + "失败 (exit=" + exec.exitCode + "): " + cmd
                    + "\n" + exec.output);
        }
    }

    private ServiceManager resolveServiceManager(String os) {
        for (ServiceManager sm : serviceManagers) {
            if (sm.supportsOs(os)) {
                return sm;
            }
        }
        throw new DeliveryException("目标 os 无服务管理适配器: " + os
                + "（支持: systemd 系 / windows WinSW）");
    }

    private SSHClient connect(TargetEnvEntity env) throws IOException {
        if (env.getHost() == null || env.getUsername() == null) {
            throw new DeliveryException("SSH 目标缺少 host/username: env=" + env.getName());
        }
        return channel.connect(env.getHost(), env.getPort() == null ? 22 : env.getPort(),
                env.getUsername(), env.getCredential());
    }

    /** ctx 缺安装目录时按 PathPolicy 生成（幂等：已有值不覆盖）。 */
    private void fillInstallDir(DeliveryContext ctx) {
        if (ctx.getRemoteInstallDir() == null || ctx.getRemoteInstallDir().isEmpty()) {
            ctx.setRemoteInstallDir(pathPolicy.installDir(
                    ctx.getTargetEnv().getOs(), ctx.getProject().getName(),
                    ctx.getRelease().getVersion()));
        }
    }

    private static String ctxVersion(DeliveryContext ctx) {
        return ctx.getProject().getName() + " " + ctx.getRelease().getVersion();
    }
}
