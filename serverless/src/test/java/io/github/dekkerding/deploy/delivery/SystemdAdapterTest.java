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
}
