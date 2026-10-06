package io.github.dekkerding.deploy.service;

import io.github.dekkerding.deploy.delivery.DeliveryContext;
import io.github.dekkerding.deploy.delivery.DeliveryProvider;
import io.github.dekkerding.deploy.domain.entity.ArtifactEntity;
import io.github.dekkerding.deploy.domain.entity.ProjectEntity;
import io.github.dekkerding.deploy.domain.entity.ReleaseEntity;
import io.github.dekkerding.deploy.domain.entity.TargetEnvEntity;
import io.github.dekkerding.deploy.domain.mapper.ArtifactMapper;
import io.github.dekkerding.deploy.domain.mapper.DeploymentMapper;
import io.github.dekkerding.deploy.domain.mapper.ProjectMapper;
import io.github.dekkerding.deploy.domain.mapper.ReleaseMapper;
import io.github.dekkerding.deploy.domain.mapper.TargetEnvMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务 6.7 验收：连续两次部署后回滚到首版本成功（specs/ssh-jar-delivery 部署回滚）。
 * 用 LOCAL stub Provider 记录调用序列，验证编排层状态机与留痕语义。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:rollback-test;DB_CLOSE_DELAY=-1",
        "deploy.storage-dir=target/rollback-test-storage"
})
class DeliveryRollbackTest {

    @TestConfiguration
    static class LocalProviderConfig {
        @Bean
        RecordingLocalProvider recordingLocalProvider() {
            return new RecordingLocalProvider();
        }
    }

    /** LOCAL 通道 stub：记录 deliver/rollback 调用序列供断言。 */
    static class RecordingLocalProvider implements DeliveryProvider {
        final List<Call> calls = new ArrayList<>();

        static class Call {
            final String action;
            final String version;

            Call(String action, String version) {
                this.action = action;
                this.version = version;
            }
        }

        @Override
        public boolean supports(String reach) {
            return "LOCAL".equalsIgnoreCase(reach);
        }

        @Override
        public String deliver(DeliveryContext ctx) {
            calls.add(new Call("deliver", ctx.getRelease().getVersion()));
            return "stub 交付成功 " + ctx.getRelease().getVersion();
        }

        @Override
        public String rollback(DeliveryContext rollbackTo, DeliveryContext current) {
            calls.add(new Call("rollback:" + current.getRelease().getVersion()
                    + "→" + rollbackTo.getRelease().getVersion(), rollbackTo.getRelease().getVersion()));
            return "stub 回滚成功 → " + rollbackTo.getRelease().getVersion();
        }
    }

    @Autowired
    private RecordingLocalProvider localProvider;
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private ReleaseMapper releaseMapper;
    @Autowired
    private ArtifactMapper artifactMapper;
    @Autowired
    private TargetEnvMapper targetEnvMapper;
    @Autowired
    private DeploymentMapper deploymentMapper;

    @org.junit.jupiter.api.BeforeEach
    void resetRecorder() {
        localProvider.calls.clear();
    }

    @Test
    void 连续两次部署后回滚到首版本成功() throws Exception {
        Long envId = seedEnv();
        Long releaseV1 = seedBuiltRelease("1.0.0");
        Long releaseV2 = seedBuiltRelease("2.0.0");

        // 两次部署均成功
        deliveryService.deploy(releaseV1, envId);
        awaitState(releaseV1, "DEPLOYED", 10000);
        deliveryService.deploy(releaseV2, envId);
        awaitState(releaseV2, "DEPLOYED", 10000);

        // 回滚 v2 → 恢复 v1
        deliveryService.rollback(releaseV2, envId);
        awaitState(releaseV2, "ROLLED_BACK", 10000);

        // 调用序列：deliver v1 → deliver v2 → rollback v2→v1
        assertThat(localProvider.calls).hasSize(3);
        assertThat(localProvider.calls.get(0).action).isEqualTo("deliver");
        assertThat(localProvider.calls.get(0).version).isEqualTo("1.0.0");
        assertThat(localProvider.calls.get(1).action).isEqualTo("deliver");
        assertThat(localProvider.calls.get(1).version).isEqualTo("2.0.0");
        assertThat(localProvider.calls.get(2).action).isEqualTo("rollback:2.0.0→1.0.0");

        // 留痕：3 行 RUNNING 起步 → v1 SUCCESS、v2 SUCCESS→ROLLED_BACK、回滚行 SUCCESS(release=v1)
        List<io.github.dekkerding.deploy.domain.entity.DeploymentEntity> rows =
                deploymentMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query
                        .QueryWrapper<io.github.dekkerding.deploy.domain.entity.DeploymentEntity>()
                        .eq("target_env_id", envId));
        assertThat(rows).hasSize(3);
        io.github.dekkerding.deploy.domain.entity.DeploymentEntity rb = rows.get(2);
        assertThat(rb.getResult()).isEqualTo("SUCCESS");
        assertThat(rb.getReleaseId()).isEqualTo(releaseV1);
        assertThat(rb.getMessage()).contains("回滚成功").contains("1.0.0");
        io.github.dekkerding.deploy.domain.entity.DeploymentEntity replaced = rows.get(1);
        assertThat(replaced.getResult()).isEqualTo("ROLLED_BACK");
        assertThat(replaced.getReleaseId()).isEqualTo(releaseV2);
    }

    @Test
    void 无更早成功版本时回滚被拒() throws Exception {
        Long envId = seedEnv();
        Long releaseV1 = seedBuiltRelease("9.0.0");
        deliveryService.deploy(releaseV1, envId);
        awaitState(releaseV1, "DEPLOYED", 10000);
        try {
            deliveryService.rollback(releaseV1, envId);
            throw new AssertionError("应抛出无更早成功版本异常");
        } catch (io.github.dekkerding.deploy.delivery.DeliveryException e) {
            assertThat(e.getMessage()).contains("没有更早的成功版本");
        }
    }

    // ---- 数据准备 ----

    private Long seedEnv() {
        TargetEnvEntity env = new TargetEnvEntity();
        env.setName("local-stub-" + System.nanoTime());
        env.setOs("linux");
        env.setArch("amd64");
        env.setRuntimeType("NATIVE");
        env.setReach("LOCAL");
        env.setProbeStatus("KNOWN");
        env.setCreatedAt(LocalDateTime.now());
        env.setUpdatedAt(LocalDateTime.now());
        targetEnvMapper.insert(env);
        return env.getId();
    }

    private Long seedBuiltRelease(String version) {
        ProjectEntity p = new ProjectEntity();
        p.setName("rb-proj-" + System.nanoTime());
        p.setBuildType("GRADLE");
        p.setSourcePath("F:/workspace/deploy/sample-apps/hello-jar");
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(p);

        ReleaseEntity r = new ReleaseEntity();
        r.setProjectId(p.getId());
        r.setVersion(version);
        r.setState("BUILT");
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        releaseMapper.insert(r);

        ArtifactEntity a = new ArtifactEntity();
        a.setReleaseId(r.getId());
        a.setProjectId(p.getId());
        a.setFileName("app-" + version + ".jar");
        a.setStoragePath("rb-proj/" + version + "/app.jar");
        a.setSizeBytes(16L);
        a.setSha256("0000000000000000000000000000000000000000000000000000000000000000");
        a.setPortable(true);
        a.setCreatedAt(LocalDateTime.now());
        artifactMapper.insert(a);
        return r.getId();
    }

    private void awaitState(Long releaseId, String expected, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            ReleaseEntity r = releaseMapper.selectById(releaseId);
            if (expected.equals(r.getState())) {
                return;
            }
            if ("FAILED".equals(r.getState())) {
                throw new AssertionError("发布单转 FAILED，failReason=" + r.getFailReason());
            }
            Thread.sleep(100);
        }
        throw new AssertionError("等待状态 " + expected + " 超时: release=" + releaseId);
    }
}
