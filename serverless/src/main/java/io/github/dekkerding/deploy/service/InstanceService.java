package io.github.dekkerding.deploy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.dekkerding.deploy.domain.entity.InstanceEntity;
import io.github.dekkerding.deploy.domain.entity.TargetEnvEntity;
import io.github.dekkerding.deploy.domain.mapper.InstanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 服务实例模型（specs/instance-scaling / design D4）：
 * 端口算术（第 i 实例监听 basePort+(i-1)）、实例清单、seq=1 向后兼容登记。
 * 旧单实例部署视为 seq=1：未配置 basePort 时沿用单实例语义（端口=healthCheckPort）。
 */
@Service
@RequiredArgsConstructor
public class InstanceService {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_STOPPED = "STOPPED";

    private final InstanceMapper instanceMapper;

    /**
     * 端口算术（specs：端口错开——基准端口 18080 扩到 3 实例 → 18080/18081/18082）。
     * 未配置 basePort：仅 seq=1 允许（单实例语义，端口沿用 healthCheckPort）。
     */
    public int portFor(TargetEnvEntity env, int seq) {
        if (seq < 1) {
            throw new IllegalArgumentException("实例编号必须 >= 1: " + seq);
        }
        if (env.getBasePort() != null) {
            return env.getBasePort() + seq - 1;
        }
        if (seq == 1 && env.getHealthCheckPort() != null) {
            return env.getHealthCheckPort();
        }
        throw new IllegalArgumentException(
                "该环境未配置 basePort，无法多实例（specs/instance-scaling）: " + env.getName());
    }

    /** 幂等登记/更新实例行（部署成功 seq=1 upsert；扩容新 seq 插入）。 */
    public InstanceEntity record(TargetEnvEntity env, Long releaseId, Long artifactId,
                                 int seq, String status) {
        int port = portFor(env, seq);
        LocalDateTime now = LocalDateTime.now();
        InstanceEntity existing = instanceMapper.selectOne(new QueryWrapper<InstanceEntity>()
                .eq("target_env_id", env.getId()).eq("seq", seq));
        if (existing == null) {
            InstanceEntity e = new InstanceEntity();
            e.setTargetEnvId(env.getId());
            e.setReleaseId(releaseId);
            e.setArtifactId(artifactId);
            e.setSeq(seq);
            e.setPort(port);
            e.setStatus(status);
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
            instanceMapper.insert(e);
            return e;
        }
        existing.setReleaseId(releaseId);
        existing.setArtifactId(artifactId);
        existing.setPort(port);
        existing.setStatus(status);
        existing.setUpdatedAt(now);
        instanceMapper.updateById(existing);
        return existing;
    }

    /** 按环境列实例（seq 升序，即实例矩阵顺序）。 */
    public List<InstanceEntity> listByEnv(Long targetEnvId) {
        return instanceMapper.selectList(new QueryWrapper<InstanceEntity>()
                .eq("target_env_id", targetEnvId).orderByAsc("seq"));
    }

    /** 删除实例行（缩容裁尾时）。 */
    public void removeByEnvAndSeq(Long targetEnvId, int seq) {
        instanceMapper.delete(new QueryWrapper<InstanceEntity>()
                .eq("target_env_id", targetEnvId).eq("seq", seq));
    }
}
