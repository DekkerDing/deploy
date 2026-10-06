package io.github.dekkerding.deploy.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.dekkerding.deploy.domain.entity.DeploymentEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeploymentMapper extends BaseMapper<DeploymentEntity> {
}
