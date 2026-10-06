package io.github.dekkerding.deploy.api;

import io.github.dekkerding.deploy.domain.entity.TargetEnvEntity;
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
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;

/** 目标环境 API（specs/delivery-routing: 注册/列表）。 */
@RestController
@RequestMapping("/api/target-envs")
@RequiredArgsConstructor
public class TargetEnvController {

    private final TargetEnvService targetEnvService;

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
}
