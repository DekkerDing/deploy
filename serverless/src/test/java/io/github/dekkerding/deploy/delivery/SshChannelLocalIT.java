package io.github.dekkerding.deploy.delivery;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本机 OpenSSH 真连集成测试（任务 6.2 验收）。
 * 默认跳过；设置 SSH_IT_KEY=私钥路径 SSH_IT_USER=用户名 后启用：
 *   ssh-keygen -t ed25519 -f /tmp/deploy_it_key -N ""
 *   （公钥按 OpenSSH 规则装入 authorized_keys）
 */
@EnabledIfEnvironmentVariable(named = "SSH_IT_KEY", matches = ".+")
class SshChannelLocalIT {

    private final SshChannel channel = new SshChannel();

    private String user() {
        String u = System.getenv("SSH_IT_USER");
        return u != null ? u : System.getProperty("user.name");
    }

    @Test
    void 对本机OpenSSH完成文件上传与命令执行() throws Exception {
        String user = user();
        try (net.schmizz.sshj.SSHClient client = channel.connect(
                "127.0.0.1", 22, user, System.getenv("SSH_IT_KEY"))) {

            // 1. 命令执行（显式 shell：cmd.exe）
            SshChannel.Execution echo = channel.exec(client, "cmd.exe /c echo ssh-exec-ok");
            assertThat(echo.isSuccess()).as(echo.output).isTrue();
            assertThat(echo.output).contains("ssh-exec-ok");

            // 2. SFTP 上传 + 回读校验（内容往返）
            String marker = "upload-roundtrip-" + System.nanoTime();
            Path local = Files.createTempFile("deploy-it-", ".txt");
            Files.write(local, marker.getBytes(StandardCharsets.UTF_8));
            String remote = Paths.get(System.getenv("TEMP"), "deploy-it-upload",
                    System.nanoTime() + "-probe.txt").toString();
            channel.upload(client, local.toString(), remote);

            SshChannel.Execution read = channel.exec(client,
                    "cmd.exe /c type \"" + remote + "\"");
            assertThat(read.isSuccess()).as(read.output).isTrue();
            assertThat(read.output).contains(marker);
        }
    }
}
