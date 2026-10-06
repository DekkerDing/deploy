package io.github.dekkerding.deploy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.dekkerding.deploy.delivery.DeliveryContext;
import io.github.dekkerding.deploy.delivery.DeliveryException;
import io.github.dekkerding.deploy.delivery.DeliveryProvider;
import io.github.dekkerding.deploy.delivery.DeliveryProviderRegistry;
import io.github.dekkerding.deploy.domain.ReleaseState;
import io.github.dekkerding.deploy.domain.entity.ArtifactEntity;
import io.github.dekkerding.deploy.domain.entity.DeploymentEntity;
import io.github.dekkerding.deploy.domain.entity.ProjectEntity;
import io.github.dekkerding.deploy.domain.entity.ReleaseEntity;
import io.github.dekkerding.deploy.domain.entity.TargetEnvEntity;
import io.github.dekkerding.deploy.domain.mapper.DeploymentMapper;
import io.github.dekkerding.deploy.domain.mapper.ReleaseMapper;
import io.github.dekkerding.deploy.service.RoutingResolver.Decision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 交付编排（specs/ssh-jar-delivery / delivery-routing）：
 * 前置校验（BUILT + 路由 + JVM 预检）→ 建 deployment 留痕 → DEPLOYING → SPI 执行 → DEPLOYED/FAILED。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final ReleaseMapper releaseMapper;
    private final DeploymentMapper deploymentMapper;
    private final ReleaseStateService releaseStateService;
    private final ProjectService projectService;
    private final TargetEnvService targetEnvService;
    private final ArtifactService artifactService;
    private final RoutingResolver routingResolver;
    private final JvmCompatPrecheck jvmCompatPrecheck;
    private final DeliveryProviderRegistry providerRegistry;

    /** 触发交付（异步执行，先同步流转 DEPLOYING）。 */
    public ReleaseEntity deploy(Long releaseId, Long targetEnvId) {
        ReleaseEntity release = releaseStateService.transition(releaseId, ReleaseState.DEPLOYING,
                "触发交付到目标环境 id=" + targetEnvId);
        new Thread(() -> {
            try {
                runDeploy(releaseId, targetEnvId);
            } catch (Exception e) {
                log.error("交付编排异常 releaseId={} targetEnvId={}", releaseId, targetEnvId, e);
            }
        }, "deploy-" + releaseId).start();
        return release;
    }

    void runDeploy(Long releaseId, Long targetEnvId) {
        DeploymentEntity deployment = null;
        try {
            deployment = precheckAndRecord(releaseId, targetEnvId);
            DeliveryContext ctx = buildContext(deployment);
            DeliveryProvider provider = providerRegistry.resolve(ctx.getTargetEnv().getReach());
            String message = provider.deliver(ctx);
            finishDeployment(deployment, "SUCCESS", message);
            releaseStateService.transition(releaseId, ReleaseState.DEPLOYED,
                    "交付成功 → " + ctx.getTargetEnv().getName() + ": " + message);
        } catch (Exception e) {
            String reason = e instanceof DeliveryException ? e.getMessage()
                    : "交付异常: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            if (deployment != null) {
                finishDeployment(deployment, "FAILED", reason);
            }
            releaseStateService.transition(releaseId, ReleaseState.FAILED,
                    "交付失败: " + reason, reason);
        }
    }

    /** 前置校验：BUILT 状态、路由决策、JVM 字节码预检、留痕行创建。 */
    private DeploymentEntity precheckAndRecord(Long releaseId, Long targetEnvId) {
        ReleaseEntity release = releaseMapper.selectById(releaseId);
        if (release == null) {
            throw new DeliveryException("发布单不存在: id=" + releaseId);
        }
        if (!ReleaseState.BUILT.name().equals(release.getState())) {
            throw new DeliveryException("仅 BUILT 状态可交付，当前: " + release.getState());
        }
        TargetEnvEntity env = targetEnvService.getByIdOrThrow(targetEnvId);

        List<ArtifactEntity> artifacts = artifactService.listByRelease(releaseId);
        Decision decision = routingResolver.resolve(artifacts, env);
        if (!decision.matched) {
            throw new DeliveryException("路由拒绝: " + decision.reason);
        }
        ArtifactEntity artifact = decision.artifact;

        // JVM 载体 + jar 制品 → 字节码预检
        if ("JVM".equalsIgnoreCase(env.getRuntimeType())
                && artifact.getFileName().toLowerCase(Locale.ROOT).endsWith(".jar")) {
            JvmCompatPrecheck.Result r = jvmCompatPrecheck.precheck(
                    artifactService.resolveStorageFile(artifact), env.getJvmVersion());
            if (!r.ok) {
                throw new DeliveryException("JVM 预检失败: " + r.message);
            }
        }

        DeploymentEntity d = new DeploymentEntity();
        d.setReleaseId(releaseId);
        d.setArtifactId(artifact.getId());
        d.setTargetEnvId(targetEnvId);
        d.setResult("RUNNING");
        d.setStartedAt(LocalDateTime.now());
        deploymentMapper.insert(d);
        return d;
    }

    private DeliveryContext buildContext(DeploymentEntity deployment) {
        ReleaseEntity release = releaseMapper.selectById(deployment.getReleaseId());
        ProjectEntity project = projectService.getByIdOrThrow(release.getProjectId());
        ArtifactEntity artifact = artifactService.getByIdOrThrow(deployment.getArtifactId());
        TargetEnvEntity env = targetEnvService.getByIdOrThrow(deployment.getTargetEnvId());
        return DeliveryContext.builder()
                .release(release)
                .project(project)
                .artifact(artifact)
                .targetEnv(env)
                .localArtifactPath(artifactService.resolveStorageFile(artifact).toString())
                .deploymentId(deployment.getId())
                .build();
    }

    private void finishDeployment(DeploymentEntity d, String result, String message) {
        d.setResult(result);
        d.setMessage(message);
        d.setFinishedAt(LocalDateTime.now());
        deploymentMapper.updateById(d);
    }

    /** 按发布单查部署记录（任务 6.8 扩展按目标查询）。 */
    public List<DeploymentEntity> listByRelease(Long releaseId) {
        return deploymentMapper.selectList(new QueryWrapper<DeploymentEntity>()
                .eq("release_id", releaseId).orderByDesc("id"));
    }
}
