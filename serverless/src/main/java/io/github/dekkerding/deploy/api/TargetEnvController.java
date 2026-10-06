package io.github.dekkerding.deploy.api;

import io.github.dekkerding.deploy.domain.entity.DeploymentEntity;
import io.github.dekkerding.deploy.domain.entity.TargetEnvEntity;
import io.github.dekkerding.deploy.service.DeliveryService;
import io.github.dekkerding.deploy.service.InstanceScalingService;
import io.github.dekkerding.deploy.service.TargetEnvService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 目标环境 API（specs/delivery-routing: 注册/列表）。 */
@RestController
@RequestMapping("/api/target-envs")
@RequiredArgsConstructor
public class TargetEnvController {

    private final TargetEnvService targetEnvService;
    private final DeliveryService deliveryService;
    private final InstanceScalingService instanceScalingService;

    @Data
    public static class RegisterTargetEnvRequest {
        @NotBlank(message = "名称不能为空")
        private String name;
        @NotBlank(message = "os 不能为空")
        private String os;
        @NotBlank(message = "arch 不能为空")
        private String arch;
        /** 仅 linux 有意义，可空 */
        private String libc;
        @NotBlank(message = "runtimeType 不能为空")
        private String runtimeType;
        @NotBlank(message = "reach 不能为空")
        private String reach;
        private String host;
        private Integer port;
        private String username;
        private String credential;
        private Integer jvmVersion;
        /** 部署后 TCP 探活端口（可选，空=跳过健康检查） */
        private Integer healthCheckPort;
        /** 多实例基准端口（可选：第 i 实例监听 basePort+(i-1)；空=单实例语义） */
        private Integer basePort;
    }

    @PostMapping
    public ResponseEntity<TargetEnvEntity> register(@Valid @RequestBody RegisterTargetEnvRequest req) {
        TargetEnvEntity env = new TargetEnvEntity();
        env.setName(req.getName());
        env.setOs(req.getOs());
        env.setArch(req.getArch());
        env.setLibc(req.getLibc());
        env.setRuntimeType(req.getRuntimeType());
        env.setReach(req.getReach());
        env.setHost(req.getHost());
        env.setPort(req.getPort());
        env.setUsername(req.getUsername());
        env.setCredential(req.getCredential());
        env.setJvmVersion(req.getJvmVersion());
        env.setHealthCheckPort(req.getHealthCheckPort());
        env.setBasePort(req.getBasePort());
        return ResponseEntity.status(HttpStatus.CREATED).body(targetEnvService.register(env));
    }

    @GetMapping
    public List<TargetEnvEntity> list() {
        return targetEnvService.list();
    }

    @GetMapping("/{id}")
    public TargetEnvEntity detail(@PathVariable Long id) {
        return targetEnvService.getByIdOrThrow(id);
    }

    /** 按目标环境查部署历史（任务 6.8，时间倒序）。 */
    @GetMapping("/{id}/deployments")
    public List<DeploymentEntity> deployments(@PathVariable Long id) {
        return deliveryService.listByTargetEnv(id);
    }

    /** 扩缩容（specs/instance-scaling：异步执行，前端轮询实例矩阵看进展）。 */
    @PostMapping("/{id}/scale")
    public ResponseEntity<Map<String, Object>> scale(@PathVariable Long id, @RequestParam int count) {
        String message = instanceScalingService.scaleTo(id, count);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        return ResponseEntity.accepted().body(body);
    }

    /** 实例矩阵（specs：每实例编号、端口、版本、运行/健康状态）。 */
    @GetMapping("/{id}/instances")
    public List<InstanceScalingService.InstanceView> instances(@PathVariable Long id) {
        return instanceScalingService.listMatrix(id);
    }
}
