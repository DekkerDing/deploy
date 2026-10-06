package io.github.dekkerding.deploy.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.dekkerding.deploy.domain.entity.InstanceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InstanceMapper extends BaseMapper<InstanceEntity> {
}
