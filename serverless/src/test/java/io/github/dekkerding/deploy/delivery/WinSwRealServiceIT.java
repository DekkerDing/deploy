package io.github.dekkerding.deploy.delivery;

import io.github.dekkerding.deploy.domain.entity.ArtifactEntity;
import io.github.dekkerding.deploy.domain.entity.ProjectEntity;
import io.github.dekkerding.deploy.domain.entity.ReleaseEntity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本机真实 WinSW 服务安装/启动集成测试（任务 6.5 验收）。
 * 环境变量 WINSW_IT=1 且依赖 vendor/winsw/WinSW-x64.exe 与
 * sample-apps/hello-server-jar/build/libs/hello-server-jar.jar 时启用。
 */
@EnabledIfEnvironmentVariable(named = "WINSW_IT", matches = "1")
class WinSwRealServiceIT {

    private static final String SERVICE_ID = "hello-server-jar";
    private static final int PORT = 18080;
    private static final Path INSTALL_DIR = Paths.get("C:\\apps\\hello-server-jar\\it-6.5\\");
    private static final WinSwAdapter ADAPTER = new WinSwAdapter();

    private static Path exe;
    private static Path jar;

    @BeforeAll
    static void setUp() throws Exception {
        Path winsw = Paths.get("..", "vendor", "winsw", "WinSW-x64.exe").toAbsolutePath().normalize();
        jar = Paths.get("..", "sample-apps", "hello-server-jar", "build", "libs", "hello-server-jar.jar")
                .toAbsolutePath().normalize();
        assertThat(Files.isRegularFile(winsw)).as("WinSW exe 缺失: " + winsw).isTrue();
        assertThat(Files.isRegularFile(jar)).as("服务 jar 缺失: " + jar).isTrue();

        Files.createDirectories(INSTALL_DIR);
        exe = INSTALL_DIR.resolve(SERVICE_ID + ".exe");
        Files.copy(winsw, exe, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(jar, INSTALL_DIR.resolve("hello-server-jar.jar"), StandardCopyOption.REPLACE_EXISTING);

        DeliveryContext ctx = ctx(INSTALL_DIR.toString());
        Path xml = INSTALL_DIR.resolve(SERVICE_ID + ".xml");
        Files.write(xml, ADAPTER.generateServiceDefinition(ctx).getBytes(StandardCharsets.UTF_8));
    }

    private static DeliveryContext ctx(String installDir) {
        ProjectEntity p = new ProjectEntity();
        p.setName(SERVICE_ID);
        ReleaseEntity r = new ReleaseEntity();
        r.setVersion("it-6.5");
        ArtifactEntity a = new ArtifactEntity();
        a.setFileName("hello-server-jar.jar");
        return DeliveryContext.builder().project(p).release(r).artifact(a)
                .remoteInstallDir(installDir).build();
    }

    private static String run(String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = p.getInputStream().read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        p.waitFor();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    void 生成本机XML并成功安装启动真实服务() throws Exception {
        // 安装 + 启动
        String installOut = run("cmd.exe", "/c", exe + " install");
        String startOut = run("cmd.exe", "/c", exe + " start");
        System.out.println("[winsw install] " + installOut);
        System.out.println("[winsw start] " + startOut);

        // sc query 状态为 RUNNING（重试窗口等待服务起来）
        String status = "";
        boolean running = false;
        for (int i = 0; i < 20 && !running; i++) {
            status = run("cmd.exe", "/c", "sc", "query", SERVICE_ID);
            running = status.contains("RUNNING");
            if (!running) {
                Thread.sleep(1000);
            }
        }
        assertThat(running).as("sc query 输出:\n" + status).isTrue();

        // TCP 探活 + 协议回显
        try (Socket socket = new Socket("127.0.0.1", PORT)) {
            socket.setSoTimeout(5000);
            new PrintWriter(socket.getOutputStream(), true).println("ping");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            assertThat(reader.readLine()).isEqualTo("hello-server-ok on " + PORT);
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        run("cmd.exe", "/c", exe + " stop");
        // 等待停止后卸载
        for (int i = 0; i < 15; i++) {
            String status = run("cmd.exe", "/c", "sc", "query", SERVICE_ID);
            if (!status.contains("RUNNING")) {
                break;
            }
            Thread.sleep(1000);
        }
        String uninstallOut = run("cmd.exe", "/c", exe + " uninstall");
        System.out.println("[winsw uninstall] " + uninstallOut);
    }
}
