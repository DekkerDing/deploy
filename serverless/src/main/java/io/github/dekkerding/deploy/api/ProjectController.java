package io.github.dekkerding.deploy.api;

import io.github.dekkerding.deploy.domain.entity.ProjectEntity;
import io.github.dekkerding.deploy.service.ProjectService;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 项目注册与查询（specs/release-management: 项目注册/重复名拒绝/列表查询）。 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    /** 支持的构建类型从注册表动态取（plugin-system：插件贡献的类型立即可见）。 */
    private final io.github.dekkerding.deploy.build.BuildExecutorRegistry executorRegistry;

    @Data
    public static class CreateProjectRequest {
        @NotBlank(message = "项目名称不能为空")
        private String name;
        @NotBlank(message = "构建类型不能为空（MAVEN/GRADLE/NPM/FLUTTER）")
        private String buildType;
        @NotBlank(message = "源码路径不能为空")
        private String sourcePath;
        private String description;
    }

    @PostMapping
    public ResponseEntity<ProjectEntity> register(@Valid @RequestBody CreateProjectRequest req) {
        String buildType = req.getBuildType().trim().toUpperCase();
        java.util.Set<String> supported = executorRegistry.supportedTypeNames();
        if (!supported.contains(buildType)) {
            throw new IllegalArgumentException("不支持的构建类型: " + req.getBuildType()
                    + "（支持: " + supported + "）");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.register(
                req.getName().trim(), buildType, req.getSourcePath().trim(), req.getDescription()));
    }

    @GetMapping
    public List<ProjectEntity> list() {
        return projectService.list();
    }

    @GetMapping("/{id}")
    public ProjectEntity detail(@PathVariable Long id) {
        return projectService.getByIdOrThrow(id);
    }
}
