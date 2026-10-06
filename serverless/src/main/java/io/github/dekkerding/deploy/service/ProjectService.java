package io.github.dekkerding.deploy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.dekkerding.deploy.domain.entity.ProjectEntity;
import io.github.dekkerding.deploy.domain.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 项目注册/查询：重名拒绝（specs: 注册重复名称项目 → 明确冲突错误）。 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;

    public ProjectEntity register(String name, String buildType, String sourcePath, String description) {
        Long exists = projectMapper.selectCount(new QueryWrapper<ProjectEntity>().eq("name", name));
        if (exists != null && exists > 0) {
            throw new DuplicateKeyException("项目名称已存在: " + name);
        }
        ProjectEntity p = new ProjectEntity();
        p.setName(name);
        p.setBuildType(buildType);
        p.setSourcePath(sourcePath);
        p.setDescription(description);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(p);
        return p;
    }

    public List<ProjectEntity> list() {
        return projectMapper.selectList(null);
    }

    public ProjectEntity getByIdOrThrow(Long id) {
        ProjectEntity p = projectMapper.selectById(id);
        if (p == null) {
            throw new IllegalArgumentException("项目不存在: id=" + id);
        }
        return p;
    }
}
