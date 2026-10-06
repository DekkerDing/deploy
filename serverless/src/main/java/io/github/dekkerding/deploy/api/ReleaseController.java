package io.github.dekkerding.deploy.api;

import io.github.dekkerding.deploy.domain.entity.DeploymentEntity;
import io.github.dekkerding.deploy.domain.entity.ReleaseEntity;
import io.github.dekkerding.deploy.service.BuildLogService;
import io.github.dekkerding.deploy.service.BuildService;
import io.github.dekkerding.deploy.service.DeliveryService;
import io.github.dekkerding.deploy.service.ReleaseService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;

/** 发布单 API（specs/release-management: 创建/触发/状态与进度查询）。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReleaseController {

    private final ReleaseService releaseService;
    private final BuildService buildService;
    private final BuildLogService buildLogService;
    private final DeliveryService deliveryService;

    @Data
    public static class CreateReleaseRequest {
        @NotBlank(message = "版本号不能为空")
        private String version;
    }

    @PostMapping("/projects/{projectId}/releases")
    public ResponseEntity<ReleaseEntity> create(@PathVariable Long projectId,
                                                @Valid @RequestBody CreateReleaseRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(releaseService.create(projectId, req.getVersion().trim()));
    }

    /** 触发构建：CREATED → BUILDING，后台异步执行真实构建（任务 3.2 挂接）。 */
    @PostMapping("/releases/{id}/trigger")
    public ReleaseEntity trigger(@PathVariable Long id) {
        return buildService.trigger(id);
    }

    @GetMapping("/releases/{id}")
    public ReleaseService.ReleaseDetail detail(@PathVariable Long id) {
        return releaseService.detail(id);
    }

    /** 构建日志增量读取：?offset=N（字节偏移），返回 nextOffset 供续读（任务 3.3）。 */
    @GetMapping("/releases/{id}/build-log")
    public BuildLogService.LogChunk buildLog(@PathVariable Long id,
                                             @RequestParam(defaultValue = "0") long offset) {
        return buildLogService.read(id, offset);
    }

    /** 触发交付（specs/ssh-jar-delivery）：BUILT → DEPLOYING → 异步执行 → DEPLOYED/FAILED。 */
    @PostMapping("/releases/{id}/deploy")
    public ReleaseEntity deploy(@PathVariable Long id, @RequestParam Long targetEnvId) {
        return deliveryService.deploy(id, targetEnvId);
    }

    /** 回滚（任务 6.7）：切回该目标环境上一次成功版本并重启+健康检查。 */
    @PostMapping("/releases/{id}/rollback")
    public ReleaseEntity rollback(@PathVariable Long id, @RequestParam Long targetEnvId) {
        return deliveryService.rollback(id, targetEnvId);
    }

    /** 按发布单查部署历史（任务 6.8，时间倒序）。 */
    @GetMapping("/releases/{id}/deployments")
    public List<DeploymentEntity> deploymentsByRelease(@PathVariable Long id) {
        return deliveryService.listByRelease(id);
    }

    @GetMapping("/projects/{projectId}/releases")
    public List<ReleaseEntity> listByProject(@PathVariable Long projectId) {
        return releaseService.listByProject(projectId);
    }
}
