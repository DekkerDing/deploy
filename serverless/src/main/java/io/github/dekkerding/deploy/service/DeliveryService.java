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

    /** 触发交付（异步执行，先同步校验 BUILT 并流转 DEPLOYING）。 */
    public ReleaseEntity deploy(Long releaseId, Long targetEnvId) {
        // BUILT 校验必须在转移 DEPLOYING 之前：异步 precheck 时状态已是 DEPLOYING
        ReleaseEntity release = releaseMapper.selectById(releaseId);
        if (release == null) {
            throw new DeliveryException("发布单不存在: id=" + releaseId);
        }
        if (!ReleaseState.BUILT.name().equals(release.getState())) {
            throw new DeliveryException("仅 BUILT 状态可交付，当前: " + release.getState());
        }
        releaseStateService.transition(releaseId, ReleaseState.DEPLOYING,
                "触发交付到目标环境 id=" + targetEnvId);
        new Thread(() -> {
            try {
                runDeploy(releaseId, targetEnvId);
            } catch (Exception e) {
                log.error("交付编排异常 releaseId={} targetEnvId={}", releaseId, targetEnvId, e);
            }
        }, "deploy-" + releaseId).start();
        return releaseMapper.selectById(releaseId);
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

    /**
     * 回滚（任务 6.7 / specs ssh-jar-delivery）：对该目标环境，把当前成功版本切回上一个成功版本。
     * 被替换的 deployment 行记 ROLLED_BACK，回滚动作本身新建留痕行；发布单状态 DEPLOYED → ROLLED_BACK。
     */
    public ReleaseEntity rollback(Long releaseId, Long targetEnvId) {
        ReleaseEntity release = releaseMapper.selectById(releaseId);
        if (release == null) {
            throw new DeliveryException("发布单不存在: id=" + releaseId);
        }
        if (!ReleaseState.DEPLOYED.name().equals(release.getState())) {
            throw new DeliveryException("仅 DEPLOYED 状态可回滚，当前: " + release.getState());
        }
        DeploymentEntity current = latestSuccessOnEnv(releaseId, targetEnvId, null);
        if (current == null) {
            throw new DeliveryException("该发布单在目标环境上没有成功部署记录: envId=" + targetEnvId);
        }
        DeploymentEntity previous = latestSuccessOnEnv(null, targetEnvId, current.getId());
        if (previous == null) {
            throw new DeliveryException("该目标环境上没有更早的成功版本可回滚: env=" + targetEnvId
                    + "，当前为 " + release.getVersion());
        }
        // 注意：状态转移只在异步完成时执行一次（DEPLOYED → ROLLED_BACK），
        // 此处不预转——否则异步成功路径将因终态二次转移而抛异常；进度由 deployment RUNNING 行体现
        new Thread(() -> {
            try {
                runRollback(releaseId, targetEnvId, current, previous);
            } catch (Exception e) {
                log.error("回滚编排异常 releaseId={} targetEnvId={}", releaseId, targetEnvId, e);
            }
        }, "rollback-" + releaseId).start();
        return release;
    }

    void runRollback(Long releaseId, Long targetEnvId,
                     DeploymentEntity current, DeploymentEntity previous) {
        DeploymentEntity record = new DeploymentEntity();
        record.setReleaseId(previous.getReleaseId());
        record.setArtifactId(previous.getArtifactId());
        record.setTargetEnvId(targetEnvId);
        record.setResult("RUNNING");
        record.setStartedAt(LocalDateTime.now());
        deploymentMapper.insert(record);
        try {
            DeliveryContext targetCtx = buildContext(previous);
            DeliveryContext currentCtx = buildContext(current);
            DeliveryProvider provider = providerRegistry.resolve(targetCtx.getTargetEnv().getReach());
            String message = provider.rollback(targetCtx, currentCtx);
            finishDeployment(record, "SUCCESS", message);
            // 被替换的当前版本留痕为 ROLLED_BACK
            current.setResult("ROLLED_BACK");
            current.setMessage("被回滚替换 → " + ctxDesc(previous));
            current.setFinishedAt(LocalDateTime.now());
            deploymentMapper.updateById(current);
            releaseStateService.transition(releaseId, ReleaseState.ROLLED_BACK,
                    "回滚完成: " + message);
        } catch (Exception e) {
            String reason = e instanceof DeliveryException ? e.getMessage()
                    : "回滚异常: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            finishDeployment(record, "FAILED", reason);
            releaseStateService.transition(releaseId, ReleaseState.FAILED,
                    "回滚失败（当前服务可能已停止，需人工介入或重新部署）: " + reason, reason);
        }
    }

    /** 该环境上最近一次 SUCCESS 部署；releaseId 非空时限定发布单，beforeId 非空时取更早者。 */
    private DeploymentEntity latestSuccessOnEnv(Long releaseId, Long targetEnvId, Long beforeId) {
        QueryWrapper<DeploymentEntity> q = new QueryWrapper<DeploymentEntity>()
                .eq("target_env_id", targetEnvId)
                .eq("result", "SUCCESS");
        if (releaseId != null) {
            q.eq("release_id", releaseId);
        }
        if (beforeId != null) {
            q.lt("id", beforeId);
        }
        List<DeploymentEntity> list = deploymentMapper.selectList(q.orderByDesc("id"));
        return list.isEmpty() ? null : list.get(0);
    }

    private static String ctxDesc(DeploymentEntity d) {
        return "deployment#" + d.getId() + " (release=" + d.getReleaseId()
                + ", artifact=" + d.getArtifactId() + ")";
    }

    /** 前置校验：路由决策、JVM 字节码预检、留痕行创建（BUILT 已在触发时同步校验，此时为 DEPLOYING）。 */
    private DeploymentEntity precheckAndRecord(Long releaseId, Long targetEnvId) {
        ReleaseEntity release = releaseMapper.selectById(releaseId);
        if (release == null) {
            throw new DeliveryException("发布单不存在: id=" + releaseId);
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
