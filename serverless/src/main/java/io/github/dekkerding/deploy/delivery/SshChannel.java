package io.github.dekkerding.deploy.delivery;

import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.sftp.SFTPClient;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * sshj 通道能力（design D8 / specs/ssh-jar-delivery）：
 * 密码或私钥认证、SFTP 上传、远程命令执行。
 *
 * “命令显式指定 shell”：exec 只下发调用方给定的完整命令串；
 * 由适配器（SystemdAdapter/WinSwAdapter）显式拼 `bash -c '...'`、
 * `powershell -Command ...` 前缀，避免依赖目标默认 shell。
 */
@Slf4j
@Component
public class SshChannel {

    public static class Execution {
        public final int exitCode;
        public final String output;

        Execution(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    /** 建立连接：credential 先按私钥路径尝试，失败/非文件回退密码认证。 */
    public SSHClient connect(String host, int port, String username, String credential)
            throws IOException {
        SSHClient client = new SSHClient();
        client.addHostKeyVerifier(new net.schmizz.sshj.transport.verification.PromiscuousVerifier()); // MVP: 首用信任（D10 安全边界内）
        client.connect(host, port);
        try {
            Path keyPath = Paths.get(credential);
            if (Files.isRegularFile(keyPath)) {
                client.authPublickey(username, keyPath.toString());
                return client;
            }
        } catch (Exception e) {
            log.debug("私钥认证未成功，回退密码认证: {}", e.getMessage());
        }
        client.authPassword(username, credential);
        return client;
    }

    /** SFTP 上传本地文件到远程绝对路径（父目录自动创建）。 */
    public void upload(SSHClient client, String localPath, String remotePath) throws IOException {
        SFTPClient sftp = client.newSFTPClient();
        try {
            mkdirsRemote(sftp, parentOf(remotePath));
            sftp.put(new FileSystemFile(localPath), remotePath);
        } finally {
            sftp.close();
        }
    }

    /** 执行完整命令串（调用方负责显式 shell 前缀），默认 10 分钟上限。 */
    public Execution exec(SSHClient client, String command) throws IOException {
        Session session = client.startSession();
        try {
            Session.Command cmd = session.exec(command);
            String output = readAll(cmd.getInputStream());
            cmd.join(10, TimeUnit.MINUTES);
            Integer exit = cmd.getExitStatus();
            String err = readAll(cmd.getErrorStream());
            return new Execution(exit == null ? -1 : exit,
                    output + (err.isEmpty() ? "" : "\n[stderr]\n" + err));
        } finally {
            session.close();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void mkdirsRemote(SFTPClient sftp, String dir) throws IOException {
        if (dir == null || dir.isEmpty() || dir.equals("/") || dir.equals("\\")) {
            return;
        }
        if (sftp.statExistence(dir) == null) {
            mkdirsRemote(sftp, parentOf(dir));
            try {
                sftp.mkdir(dir);
            } catch (Exception alreadyExists) {
                if (sftp.statExistence(dir) == null) {
                    throw alreadyExists;
                }
            }
        }
    }

    private static String parentOf(String path) {
        int pos1 = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return pos1 <= 0 ? null : path.substring(0, pos1);
    }
}
