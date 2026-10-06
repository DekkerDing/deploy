package io.github.dekkerding.deploy.service;

import io.github.dekkerding.deploy.domain.entity.InstanceEntity;
import io.github.dekkerding.deploy.domain.entity.TargetEnvEntity;
import io.github.dekkerding.deploy.domain.mapper.InstanceMapper;
import io.github.dekkerding.deploy.domain.mapper.TargetEnvMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 任务 5.1 验收（specs/instance-scaling 实例模型与端口分配）：
 * 端口算术（基准端口+(i-1)）、未配置 basePort 的单实例语义、实例行唯一约束与幂等登记。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:instance-test;DB_CLOSE_DELAY=-1",
        "deploy.storage-dir=target/instance-test-storage"
})
class InstanceServiceTest {

    @Autowired
    private InstanceService instanceService;
    @Autowired
    private InstanceMapper instanceMapper;
    @Autowired
    private TargetEnvMapper targetEnvMapper;

    @Test
    void 端口算术基准端口依次错开() {
        TargetEnvEntity env = seedEnv(18080, null);
        assertThat(instanceService.portFor(env, 1)).isEqualTo(18080);
        assertThat(instanceService.portFor(env, 2)).isEqualTo(18081);
        assertThat(instanceService.portFor(env, 3)).isEqualTo(18082);
    }

    @Test
    void 未配置basePort时seq1沿用探活端口而多实例被拒() {
        // 旧环境兼容：只有 healthCheckPort（单实例语义）
        TargetEnvEntity env = seedEnv(null, 18080);
        assertThat(instanceService.portFor(env, 1)).isEqualTo(18080);
        assertThatThrownBy(() -> instanceService.portFor(env, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未配置 basePort");
        assertThatThrownBy(() -> instanceService.portFor(env, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(">= 1");
    }

    @Test
    void 实例行幂等登记与矩阵查询() {
        TargetEnvEntity env = seedEnv(18100, null);
        instanceService.record(env, 11L, 21L, 1, InstanceService.STATUS_RUNNING);
        // 同 seq 重复登记 = 更新而非重复插入（部署成功后 upsert）
        instanceService.record(env, 12L, 22L, 1, InstanceService.STATUS_RUNNING);
        instanceService.record(env, 12L, 22L, 2, InstanceService.STATUS_RUNNING);

        assertThat(instanceService.listByEnv(env.getId())).hasSize(2);
        InstanceEntity seq1 = instanceService.listByEnv(env.getId()).get(0);
        assertThat(seq1.getSeq()).isEqualTo(1);
        assertThat(seq1.getPort()).isEqualTo(18100);
        assertThat(seq1.getReleaseId()).isEqualTo(12L); // 更新为最新版本
        InstanceEntity seq2 = instanceService.listByEnv(env.getId()).get(1);
        assertThat(seq2.getPort()).isEqualTo(18101);

        instanceService.removeByEnvAndSeq(env.getId(), 2);
        assertThat(instanceService.listByEnv(env.getId())).hasSize(1);
    }

    @Test
    void 同环境同seq唯一约束兜底() {
        TargetEnvEntity env = seedEnv(18200, null);
        instanceService.record(env, 11L, 21L, 1, InstanceService.STATUS_RUNNING);
        // 绕过幂等登记直接插重复 seq → 数据库唯一约束（uk_service_instance_env_seq）
        InstanceEntity dup = new InstanceEntity();
        dup.setTargetEnvId(env.getId());
        dup.setReleaseId(11L);
        dup.setArtifactId(21L);
        dup.setSeq(1);
        dup.setPort(18200);
        dup.setStatus(InstanceService.STATUS_RUNNING);
        dup.setCreatedAt(LocalDateTime.now());
        dup.setUpdatedAt(LocalDateTime.now());
        assertThatThrownBy(() -> instanceMapper.insert(dup))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private TargetEnvEntity seedEnv(Integer basePort, Integer healthCheckPort) {
        TargetEnvEntity env = new TargetEnvEntity();
        env.setName("inst-env-" + System.nanoTime());
        env.setOs("windows");
        env.setArch("amd64");
        env.setRuntimeType("JVM");
        env.setReach("LOCAL");
        env.setProbeStatus("KNOWN");
        env.setBasePort(basePort);
        env.setHealthCheckPort(healthCheckPort);
        env.setCreatedAt(LocalDateTime.now());
        env.setUpdatedAt(LocalDateTime.now());
        targetEnvMapper.insert(env);
        return env;
    }
}
