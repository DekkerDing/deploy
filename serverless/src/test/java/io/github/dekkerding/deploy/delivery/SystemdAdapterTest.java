package io.github.dekkerding.deploy.delivery;

import io.github.dekkerding.deploy.domain.entity.ArtifactEntity;
import io.github.dekkerding.deploy.domain.entity.ProjectEntity;
import io.github.dekkerding.deploy.domain.entity.ReleaseEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SystemdAdapterTest {

    private final SystemdAdapter adapter = new SystemdAdapter();

    private DeliveryContext ctx() {
        ProjectEntity p = new ProjectEntity();
        p.setName("demo-app");
        ReleaseEntity r = new ReleaseEntity();
        r.setVersion("1.2.3");
        ArtifactEntity a = new ArtifactEntity();
        a.setFileName("demo-app.jar");
        return DeliveryContext.builder()
                .project(p).release(r).artifact(a)
                .remoteInstallDir("/opt/demo-app/1.2.3/")
                .build();
    }

    @Test
    void unit模板包含关键指令() {
        String unit = adapter.generateServiceDefinition(ctx());
        assertThat(unit).contains("[Unit]").contains("[Service]").contains("[Install]");
        assertThat(unit).contains("Description=demo-app 1.2.3");
        assertThat(unit).contains("ExecStart=/usr/bin/java -jar /opt/demo-app/1.2.3/demo-app.jar");
        assertThat(unit).contains("WorkingDirectory=/opt/demo-app/1.2.3/");
        assertThat(unit).contains("Restart=on-failure");
        assertThat(unit).contains("WantedBy=multi-user.target");
    }

    @Test
    void 服务定义路径与激活命令() {
        assertThat(adapter.serviceDefinitionPath(ctx()))
                .isEqualTo("/etc/systemd/system/demo-app.service");
        List<String> cmds = adapter.activateCommands(ctx());
        assertThat(cmds).containsExactly(
                "systemctl daemon-reload",
                "systemctl enable demo-app.service",
                "systemctl restart demo-app.service");
        assertThat(adapter.statusCommand(ctx())).isEqualTo("systemctl is-active demo-app.service");
    }

    @Test
    void 支持类Unix目标() {
        assertThat(adapter.supportsOs("linux")).isTrue();
        assertThat(adapter.supportsOs("freebsd")).isTrue();
        assertThat(adapter.supportsOs("windows")).isFalse();
    }

    /** 任务 5.2：多实例 app@N 单元（design D4 systemd 模板单元命名）+ 端口注入 + start 不 restart。 */
    @Test
    void 多实例单元名带实例号且注入端口() {
        DeliveryContext ctx2 = ctx().toBuilder().instanceSeq(2).instancePort(18081).build();
        assertThat(adapter.unitName(ctx2)).isEqualTo("demo-app@2.service");
        assertThat(adapter.serviceDefinitionPath(ctx2))
                .isEqualTo("/etc/systemd/system/demo-app@2.service");
        String unit = adapter.generateServiceDefinition(ctx2);
        assertThat(unit).contains("--server.port=18081");
        assertThat(unit).contains("(instance 2)");
        assertThat(adapter.activateCommands(ctx2)).containsExactly(
                "systemctl daemon-reload",
                "systemctl enable demo-app@2.service",
                "systemctl start demo-app@2.service"); // 新实例 start，不 restart 存量
        assertThat(adapter.deactivateCommands(ctx2))
                .containsExactly("systemctl stop demo-app@2.service");
        assertThat(adapter.statusCommand(ctx2)).isEqualTo("systemctl is-active demo-app@2.service");
        // 单实例路径不受影响（无端口注入、restart 语义保持）
        assertThat(adapter.generateServiceDefinition(ctx())).doesNotContain("--server.port");
        assertThat(adapter.activateCommands(ctx()).get(2)).isEqualTo("systemctl restart demo-app.service");
    }
}
