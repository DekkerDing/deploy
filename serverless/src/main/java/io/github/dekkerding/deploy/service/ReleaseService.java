package io.github.dekkerding.deploy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.dekkerding.deploy.domain.ReleaseState;
import io.github.dekkerding.deploy.domain.entity.ArtifactEntity;
import io.github.dekkerding.deploy.domain.entity.DeploymentEntity;
import io.github.dekkerding.deploy.domain.entity.ProjectEntity;
import io.github.dekkerding.deploy.domain.entity.ReleaseEntity;
import io.github.dekkerding.deploy.domain.entity.ReleaseEventEntity;
import io.github.dekkerding.deploy.domain.mapper.ArtifactMapper;
import io.github.dekkerding.deploy.domain.mapper.DeploymentMapper;
import io.github.dekkerding.deploy.domain.mapper.ReleaseEventMapper;
import io.github.dekkerding.deploy.domain.mapper.ReleaseMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 发布单：创建（CREATED）、详情聚合（状态+时间线+制品+部署记录）、触发。 */
@Service
@RequiredArgsConstructor
public class ReleaseService {

    private final ReleaseMapper releaseMapper;
    private final ReleaseEventMapper releaseEventMapper;
    private final ArtifactMapper artifactMapper;
    private final DeploymentMapper deploymentMapper;
    private final ProjectService projectService;
    private final ReleaseStateService releaseStateService;

    public ReleaseEntity create(Long projectId, String version) {
        ProjectEntity project = projectService.getByIdOrThrow(projectId);
        Long exists = releaseMapper.selectCount(new QueryWrapper<ReleaseEntity>()
                .eq("project_id", projectId).eq("version", version));
        if (exists != null && exists > 0) {
            throw new DuplicateKeyException(
                    "发布单已存在: 项目=" + project.getName() + ", version=" + version);
        }
        ReleaseEntity r = new ReleaseEntity();
        r.setProjectId(projectId);
        r.setVersion(version);
        r.setState(ReleaseState.CREATED.name());
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        releaseMapper.insert(r);

        ReleaseEventEntity e = new ReleaseEventEntity();
        e.setReleaseId(r.getId());
        e.setFromState(null);
        e.setToState(ReleaseState.CREATED.name());
        e.setMessage("发布单创建 (project=" + project.getName() + ", version=" + version + ")");
        e.setCreatedAt(LocalDateTime.now());
        releaseEventMapper.insert(e);
        return r;
    }

    /** 触发构建：CREATED → BUILDING（真实构建执行在 BuildExecutor 任务组接入）。 */
    public ReleaseEntity triggerBuild(Long releaseId) {
        return releaseStateService.transition(releaseId, ReleaseState.BUILDING, "触发构建");
    }

    @Data
    public static class ReleaseDetail {
        private ReleaseEntity release;
        private ProjectEntity project;
        private List<ReleaseEventEntity> timeline;
        private List<ArtifactEntity> artifacts;
        private List<DeploymentEntity> deployments;
        private String buildLogUrl;
    }

    public ReleaseDetail detail(Long releaseId) {
        ReleaseEntity r = releaseMapper.selectById(releaseId);
        if (r == null) {
            throw new IllegalArgumentException("发布单不存在: id=" + releaseId);
        }
        ReleaseDetail d = new ReleaseDetail();
        d.setRelease(r);
        d.setProject(projectService.getByIdOrThrow(r.getProjectId()));
        d.setTimeline(releaseEventMapper.selectList(new QueryWrapper<ReleaseEventEntity>()
                .eq("release_id", releaseId).orderByAsc("id")));
        d.setArtifacts(artifactMapper.selectList(new QueryWrapper<ArtifactEntity>()
                .eq("release_id", releaseId).orderByAsc("id")));
        d.setDeployments(deploymentMapper.selectList(new QueryWrapper<DeploymentEntity>()
                .eq("release_id", releaseId).orderByAsc("id")));
        d.setBuildLogUrl("/api/releases/" + releaseId + "/build-log?offset=0");
        return d;
    }

    public List<ReleaseEntity> listByProject(Long projectId) {
        return releaseMapper.selectList(new QueryWrapper<ReleaseEntity>()
                .eq("project_id", projectId).orderByDesc("id"));
    }
}
