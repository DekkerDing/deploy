package io.github.dekkerding.deploy.delivery;

import io.github.dekkerding.deploy.domain.entity.ArtifactEntity;
import io.github.dekkerding.deploy.domain.entity.ProjectEntity;
import io.github.dekkerding.deploy.domain.entity.ReleaseEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WinSwAdapterTest {

    private WinSwAdapter adapter() throws Exception {
        WinSwAdapter a = new WinSwAdapter();
        Field f = WinSwAdapter.class.getDeclaredField("winswExePath");
        f.setAccessible(true);
        f.set(a, "nonexistent-winsw.exe"); // platformWinswExe 才用到，模板生成不依赖
        return a;
    }

    private DeliveryContext ctx() {
        ProjectEntity p = new ProjectEntity();
        p.setName("hello-server-jar");
        ReleaseEntity r = new ReleaseEntity();
        r.setVersion("1.0.0");
        ArtifactEntity a = new ArtifactEntity();
        a.setFileName("hello-server-jar.jar");
        return DeliveryContext.builder()
                .project(p).release(r).artifact(a)
                .remoteInstallDir("C:\\apps\\hello-server-jar\\1.0.0\\")
                .build();
    }

    @Test
    void XML模板包含服务标识与制品指向() throws Exception {
        String xml = adapter().generateServiceDefinition(ctx());
        assertThat(xml).contains("<id>hello-server-jar</id>");
        assertThat(xml).contains("<executable>" + System.getProperty("java.home")
                + "\\bin\\java.exe</executable>");
        assertThat(xml).contains("<arguments>-jar \"C:\\apps\\hello-server-jar\\1.0.0\\hello-server-jar.jar\"</arguments>");
        assertThat(xml).contains("<workingdirectory>C:\\apps\\hello-server-jar\\1.0.0\\</workingdirectory>");
        assertThat(xml).contains("<onfailure action=\"restart\"");
        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    }

    @Test
    void 落点与激活命令指向新版本制品() throws Exception {
        WinSwAdapter a = adapter();
        assertThat(a.serviceDefinitionPath(ctx()))
                .isEqualTo("C:\\apps\\hello-server-jar\\1.0.0\\hello-server-jar.xml");
        assertThat(a.targetWinswExePath(ctx()))
                .isEqualTo("C:\\apps\\hello-server-jar\\1.0.0\\hello-server-jar.exe");
        List<String> cmds = a.activateCommands(ctx());
        assertThat(cmds).containsExactly(
                "\"C:\\apps\\hello-server-jar\\1.0.0\\hello-server-jar.exe\" install",
                "\"C:\\apps\\hello-server-jar\\1.0.0\\hello-server-jar.exe\" start");
        assertThat(a.statusCommand(ctx())).isEqualTo("sc query hello-server-jar");
    }

    @Test
    void 仅支持windows目标() throws Exception {
        WinSwAdapter a = adapter();
        assertThat(a.supportsOs("windows")).isTrue();
        assertThat(a.supportsOs("linux")).isFalse();
        assertThat(a.serviceId(ctx())).isEqualTo("hello-server-jar");
    }

    /** 任务 5.2：多实例服务名 -N 后缀 + 端口注入（design D4 WinSW app-N）。 */
    private DeliveryContext instanceCtx(int seq, int port) {
        return ctx().toBuilder().instanceSeq(seq).instancePort(port).build();
    }

    @Test
    void 多实例服务名带后缀且注入端口() throws Exception {
        WinSwAdapter a = adapter();
        DeliveryContext ctx2 = instanceCtx(2, 18081);
        assertThat(a.serviceId(ctx2)).isEqualTo("hello-server-jar-2");
        assertThat(a.serviceDefinitionPath(ctx2))
                .isEqualTo("C:\\apps\\hello-server-jar\\1.0.0\\hello-server-jar-2.xml");
        assertThat(a.targetWinswExePath(ctx2))
                .isEqualTo("C:\\apps\\hello-server-jar\\1.0.0\\hello-server-jar-2.exe");
        String xml = a.generateServiceDefinition(ctx2);
        assertThat(xml).contains("<id>hello-server-jar-2</id>");
        assertThat(xml).contains("--server.port=18081");
        // 单实例路径不受影响（无端口注入）
        assertThat(adapter().generateServiceDefinition(ctx())).doesNotContain("--server.port");
        assertThat(a.statusCommand(ctx2)).isEqualTo("sc query hello-server-jar-2");
        assertThat(a.deactivateCommands(ctx2)).containsExactly(
                "\"C:\\apps\\hello-server-jar\\1.0.0\\hello-server-jar-2.exe\" stop",
                "\"C:\\apps\\hello-server-jar\\1.0.0\\hello-server-jar-2.exe\" uninstall");
    }
}
