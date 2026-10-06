package io.github.dekkerding.deploy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.dekkerding.deploy.delivery.DeliveryContext;
import io.github.dekkerding.deploy.delivery.DeliveryException;
import io.github.dekkerding.deploy.delivery.DeliveryProvider;
import io.github.dekkerding.deploy.delivery.DeliveryProviderRegistry;
import io.github.dekkerding.deploy.delivery.HealthChecker;
import io.github.dekkerding.deploy.delivery.PathPolicy;
import io.github.dekkerding.deploy.delivery.SshDeliveryProvider;
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
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 扩缩容编排（specs/instance-scaling / design D4）：
 * 扩容 = 对新 seq 逐个走完整 deliver（服务名带实例后缀 + 实例端口注入 + 实例级探活）；
 * 缩容 = 从最大编号起逐个停用注销并删实例行（保留实例不受影响）。
 * 版本来源 = 该环境最近一次成功部署的发布单/制品。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstanceScalingService {

    private final TargetEnvMapper targetEnvMapper;
    private final ReleaseMapper releaseMapper;
    private final ProjectMapper projectMapper;
    private final ArtifactMapper artifactMapper;
    private final DeploymentMapper deploymentMapper;
    private final ArtifactService artifactService;
    private final InstanceService instanceService;
    private final DeliveryProviderRegistry providerRegistry;
    private final PathPolicy pathPolicy;
    private final HealthChecker healthChecker;

    /** 实例矩阵视图（specs：每实例展示编号、端口、所属发布单版本、运行/健康状态）。 */
    @Data
    public static class InstanceView {
        private Long id;
        private Integer seq;
        private Integer port;
        private Long releaseId;
        private String version;
        private String status;
        private Boolean healthy;
        private String healthMessage;
    }

    /**
     * 扩缩容到目标实例数（同步校验 + 异步执行，前端轮询实例矩阵看进展）。
     */
    public String scaleTo(Long envId, int targetCount) {
        if (targetCount < 1) {
            throw new DeliveryException("目标实例数必须 >= 1: " + targetCount);
        }
        TargetEnvEntity env = targetEnvMapper.selectById(envId);
        if (env == null) {
            throw new DeliveryException("目标环境不存在: id=" + envId);
        }
        if (targetCount > 1 && env.getBasePort() == null) {
            throw new DeliveryException("该环境未配置 basePort，无法扩容到 " + targetCount
                    + " 实例: " + env.getName());
        }
        DeploymentEntity latest = latestSuccessOnEnv(envId);
        if (latest == null) {
            throw new DeliveryException("该环境尚无成功部署，请先完成一次单实例部署: " + env.getName());
        }
        int currentCount = instanceService.listByEnv(envId).size();
        if (targetCount == currentCount) {
            return "实例数已是 " + targetCount + "，无需调整（" + env.getName() + "）";
        }
        new Thread(() -> {
            try {
                if (targetCount > currentCount) {
                    scaleUp(env, latest, currentCount + 1, targetCount);
                } else {
                    // 裁尾到 targetCount：删除 seq ∈ [targetCount+1, currentCount]
                    scaleDown(env, currentCount, targetCount + 1);
                }
            } catch (Exception e) {
                log.error("扩缩容执行异常 envId={} targetCount={}", envId, targetCount, e);
            }
        }, "scale-" + envId).start();
        return "扩缩容已发起: " + currentCount + " → " + targetCount
                + " 实例（" + env.getName() + "）";
    }

    /** 逐个拉起新实例（specs：全部通过方记成功；某实例失败即终止，已健康实例不受影响）。 */
    private void scaleUp(TargetEnvEntity env, DeploymentEntity latest, int fromSeq, int toSeq) {
        for (int seq = fromSeq; seq <= toSeq; seq++) {
            int port = instanceService.portFor(env, seq);
            DeliveryContext ctx = buildInstanceCtx(env, latest.getReleaseId(),
                    latest.getArtifactId(), seq, port);
            DeliveryProvider provider = providerRegistry.resolve(env.getReach());
            String message = provider.deliver(ctx); // 含实例级探活（instancePort）
            instanceService.record(env, latest.getReleaseId(), latest.getArtifactId(), seq,
                    InstanceService.STATUS_RUNNING);
            log.info("扩容实例 seq={} port={} 成功: {}", seq, port, message);
        }
    }

    /** 从最大编号起裁减（specs 缩容裁尾：实例 3、2 依次停止注销，实例 1 持续运行）。 */
    private void scaleDown(TargetEnvEntity env, int fromSeq, int toSeq) {
        DeliveryProvider provider = providerRegistry.resolve(env.getReach());
        for (int seq = fromSeq; seq >= toSeq; seq--) {
            final int targetSeq = seq; // lambda 捕获要求事实 final
            InstanceEntity inst = instanceService.listByEnv(env.getId()).stream()
                    .filter(i -> i.getSeq() == targetSeq).findFirst().orElse(null);
            if (inst == null) {
                continue;
            }
            DeliveryContext ctx = buildInstanceCtx(env, inst.getReleaseId(),
                    inst.getArtifactId(), seq, inst.getPort());
            // SSH 通道走真实停用注销；LOCAL/stub 通道无远程服务动作，仅删实例行
            if (provider instanceof SshDeliveryProvider) {
                ((SshDeliveryProvider) provider).deactivateService(ctx);
            }
            instanceService.removeByEnvAndSeq(env.getId(), seq);
            log.info("缩容实例 seq={} port={} 已停止注销", seq, inst.getPort());
        }
    }

    /** 实例矩阵：清单 + RUNNING 实例的实时 TCP 探活。 */
    public List<InstanceView> listMatrix(Long envId) {
        TargetEnvEntity env = targetEnvMapper.selectById(envId);
        if (env == null) {
            throw new IllegalArgumentException("目标环境不存在: id=" + envId);
        }
        List<InstanceView> views = new ArrayList<>();
        for (InstanceEntity inst : instanceService.listByEnv(envId)) {
            InstanceView v = new InstanceView();
            v.setId(inst.getId());
            v.setSeq(inst.getSeq());
            v.setPort(inst.getPort());
            v.setReleaseId(inst.getReleaseId());
            ReleaseEntity release = releaseMapper.selectById(inst.getReleaseId());
            v.setVersion(release != null ? release.getVersion() : null);
            v.setStatus(inst.getStatus());
            if (InstanceService.STATUS_RUNNING.equals(inst.getStatus())) {
                HealthChecker.HealthResult r = healthChecker.check(env.getHost(), inst.getPort());
                v.setHealthy(r.healthy);
                v.setHealthMessage(r.message);
            }
            views.add(v);
        }
        return views;
    }

    private DeliveryContext buildInstanceCtx(TargetEnvEntity env, Long releaseId, Long artifactId,
                                             Integer seq, Integer port) {
        ReleaseEntity release = releaseMapper.selectById(releaseId);
        if (release == null) {
            throw new DeliveryException("发布单不存在: id=" + releaseId);
        }
        ProjectEntity project = projectMapper.selectById(release.getProjectId());
        ArtifactEntity artifact = artifactMapper.selectById(artifactId);
        return DeliveryContext.builder()
                .release(release)
                .project(project)
                .artifact(artifact)
                .targetEnv(env)
                .localArtifactPath(artifactService.resolveStorageFile(artifact).toString())
                .remoteInstallDir(pathPolicy.installDir(env.getOs(), project.getName(),
                        release.getVersion()))
                .instanceSeq(seq)
                .instancePort(port)
                // 制品已由首次交付上传至共享版本目录；运行中实例锁着 jar（Windows），
                // 重传必失败——扩缩容只上传实例独立的服务定义与 WinSW exe
                .skipArtifactUpload(true)
                .build();
    }

    private DeploymentEntity latestSuccessOnEnv(Long envId) {
        List<DeploymentEntity> list = deploymentMapper.selectList(
                new QueryWrapper<DeploymentEntity>()
                        .eq("target_env_id", envId)
                        .eq("result", "SUCCESS")
                        .orderByDesc("id"));
        return list.isEmpty() ? null : list.get(0);
    }
}
