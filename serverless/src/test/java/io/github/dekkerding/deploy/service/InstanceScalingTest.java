package io.github.dekkerding.deploy.service;

import io.github.dekkerding.deploy.delivery.DeliveryContext;
import io.github.dekkerding.deploy.delivery.DeliveryProvider;
import io.github.dekkerding.deploy.domain.entity.ArtifactEntity;
import io.github.dekkerding.deploy.domain.entity.DeploymentEntity;
import io.github.dekkerding.deploy.domain.entity.InstanceEntity;
import io.github.dekkerding.deploy.domain.entity.ProjectEntity;
import io.github.dekkerding.deploy.domain.entity.ReleaseEntity;
import io.github.dekkerding.deploy.domain.entity.TargetEnvEntity;
import io.github.dekkerding.deploy.domain.mapper.ArtifactMapper;
import io.github.dekkerding.deploy.domain.mapper.DeploymentMapper;
import io.github.dekkerding.deploy.domain.mapper.ProjectMapper;
import io.github.dekkerding.deploy.domain.mapper.ReleaseMapper;
import io.github.dekkerding.deploy.domain.mapper.TargetEnvMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务 5.3 验收（specs/instance-scaling 编排逻辑）：
 * 扩容对新 seq 逐个 deliver（实例参数注入）、失败终止且存量不受影响、缩容裁尾删行、无成功部署拒绝扩容。
 * 真机 1→3→1 全链路在任务 5.3 真机环境另验（SSH + WinSW 实端口）。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:scaling-test;DB_CLOSE_DELAY=-1",
        "deploy.storage-dir=target/scaling-test-storage"
})
class InstanceScalingTest {

    @TestConfiguration
    static class LocalProviderConfig {
        @Bean
        ScalingStubProvider scalingStubProvider() {
            return new ScalingStubProvider();
        }
    }

    /** LOCAL stub：记录每次 deliver 的实例参数，可注入指定 seq 失败。 */
    static class ScalingStubProvider implements DeliveryProvider {
        final List<String> delivers = new ArrayList<>();
        volatile int failAtSeq = -1;

        @Override
        public boolean supports(String reach) {
            return "LOCAL".equalsIgnoreCase(reach);
        }

        @Override
        public String deliver(DeliveryContext ctx) {
            delivers.add("seq=" + ctx.getInstanceSeq() + " port=" + ctx.getInstancePort()
                    + " svc含-N=" + (ctx.getRemoteInstallDir() != null));
            if (ctx.getInstanceSeq() != null && ctx.getInstanceSeq() == failAtSeq) {
                throw new io.github.dekkerding.deploy.delivery.DeliveryException(
                        "注入失败: 实例 " + ctx.getInstanceSeq() + " 健康检查超时");
            }
            return "stub 实例 seq=" + ctx.getInstanceSeq() + " 启动成功";
        }

        @Override
        public String rollback(DeliveryContext rollbackTo, DeliveryContext current) {
            return "stub 回滚（扩缩容测试不使用）";
        }
    }

    @Autowired
    private ScalingStubProvider stub;
    @Autowired
    private InstanceScalingService scalingService;
    @Autowired
    private InstanceService instanceService;
    @Autowired
    private TargetEnvMapper targetEnvMapper;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private ReleaseMapper releaseMapper;
    @Autowired
    private ArtifactMapper artifactMapper;
    @Autowired
    private DeploymentMapper deploymentMapper;

    @BeforeEach
    void reset() {
        stub.delivers.clear();
        stub.failAtSeq = -1;
    }

    @Test
    void 扩容逐实例交付端口错开且登记矩阵() throws Exception {
        Long envId = seedEnv(18080);
        seedSuccessDeployment(envId, "1.0.0");

        String msg = scalingService.scaleTo(envId, 3);
        assertThat(msg).contains("已发起");
        awaitInstanceCount(envId, 3, 10000);

        // 新实例 seq 2、3 逐个 deliver（seq=1 已由部署登记），端口按 basePort+seq-1 错开
        assertThat(stub.delivers).containsExactly(
                "seq=2 port=18081 svc含-N=true",
                "seq=3 port=18082 svc含-N=true");
        List<InstanceEntity> matrix = instanceService.listByEnv(envId);
        assertThat(matrix).extracting(InstanceEntity::getPort)
                .containsExactly(18080, 18081, 18082);
        assertThat(matrix).extracting(InstanceEntity::getStatus)
                .containsOnly(InstanceService.STATUS_RUNNING);
    }

    @Test
    void 扩容中实例失败终止且已健康实例不受影响() throws Exception {
        Long envId = seedEnv(18100);
        seedSuccessDeployment(envId, "1.0.0");

        stub.failAtSeq = 3; // 实例 3 探活超时（specs：记失败并给诊断，存量不受影响）
        scalingService.scaleTo(envId, 3);
        Thread.sleep(1500); // 异步：实例 2 成功、实例 3 失败终止（实例 4 不存在）

        List<InstanceEntity> matrix = instanceService.listByEnv(envId);
        assertThat(matrix).hasSize(2); // seq 1、2 存活
        assertThat(matrix).extracting(InstanceEntity::getSeq).containsExactly(1, 2);
        assertThat(stub.delivers).hasSize(2); // 第 3 次 deliver 抛出，无后续
    }

    @Test
    void 缩容裁尾从最大编号起删行() throws Exception {
        Long envId = seedEnv(18200);
        seedSuccessDeployment(envId, "1.0.0");
        scalingService.scaleTo(envId, 3);
        awaitInstanceCount(envId, 3, 10000);

        scalingService.scaleTo(envId, 1);
        awaitInstanceCount(envId, 1, 10000);
        List<InstanceEntity> matrix = instanceService.listByEnv(envId);
        assertThat(matrix).hasSize(1);
        assertThat(matrix.get(0).getSeq()).isEqualTo(1); // 实例 1 保留
        assertThat(matrix.get(0).getStatus()).isEqualTo(InstanceService.STATUS_RUNNING);
    }

    @Test
    void 无成功部署与未配basePort的扩容被拒() throws Exception {
        Long envId = seedEnv(null); // 无 basePort
        try {
            scalingService.scaleTo(envId, 2);
            throw new AssertionError("应拒绝未配置 basePort 的扩容");
        } catch (io.github.dekkerding.deploy.delivery.DeliveryException e) {
            assertThat(e.getMessage()).contains("basePort");
        }
        // 有 basePort 但无成功部署
        Long envId2 = seedEnv(18300);
        try {
            scalingService.scaleTo(envId2, 2);
            throw new AssertionError("应拒绝无成功部署的扩容");
        } catch (io.github.dekkerding.deploy.delivery.DeliveryException e) {
            assertThat(e.getMessage()).contains("尚无成功部署");
        }
    }

    // ---- 数据准备 ----

    private Long seedEnv(Integer basePort) {
        TargetEnvEntity env = new TargetEnvEntity();
        env.setName("scale-env-" + System.nanoTime());
        env.setOs("linux");
        env.setArch("amd64");
        env.setRuntimeType("NATIVE");
        env.setReach("LOCAL");
        env.setProbeStatus("KNOWN");
        env.setBasePort(basePort);
        env.setHealthCheckPort(basePort); // portFor 兼容 seq=1
        env.setCreatedAt(LocalDateTime.now());
        env.setUpdatedAt(LocalDateTime.now());
        targetEnvMapper.insert(env);
        return env.getId();
    }

    /** 造一次成功部署留痕 + seq=1 实例行（模拟已运行单实例）。 */
    private void seedSuccessDeployment(Long envId, String version) {
        ProjectEntity p = new ProjectEntity();
        p.setName("scale-proj-" + System.nanoTime());
        p.setBuildType("GRADLE");
        p.setSourcePath("F:/workspace/deploy/sample-apps/hello-jar");
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(p);

        ReleaseEntity r = new ReleaseEntity();
        r.setProjectId(p.getId());
        r.setVersion(version);
        r.setState("DEPLOYED");
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        releaseMapper.insert(r);

        ArtifactEntity a = new ArtifactEntity();
        a.setReleaseId(r.getId());
        a.setProjectId(p.getId());
        a.setFileName("app-" + version + ".jar");
        a.setStoragePath("scale-proj/" + version + "/app.jar");
        a.setSizeBytes(16L);
        a.setSha256("00" + Math.abs(new AtomicInteger().incrementAndGet()));
        a.setPortable(true);
        a.setCreatedAt(LocalDateTime.now());
        artifactMapper.insert(a);

        DeploymentEntity d = new DeploymentEntity();
        d.setReleaseId(r.getId());
        d.setArtifactId(a.getId());
        d.setTargetEnvId(envId);
        d.setResult("SUCCESS");
        d.setStartedAt(LocalDateTime.now());
        d.setFinishedAt(LocalDateTime.now());
        d.setMessage("seed");
        deploymentMapper.insert(d);

        TargetEnvEntity env = targetEnvMapper.selectById(envId);
        instanceService.record(env, r.getId(), a.getId(), 1, InstanceService.STATUS_RUNNING);
    }

    private void awaitInstanceCount(Long envId, int expected, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (instanceService.listByEnv(envId).size() == expected) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("等待实例数 " + expected + " 超时: env=" + envId
                + " 当前=" + instanceService.listByEnv(envId));
    }
}
