package io.github.dekkerding.deploy.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.dekkerding.deploy.domain.entity.ProjectEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMapper extends BaseMapper<ProjectEntity> {
}
