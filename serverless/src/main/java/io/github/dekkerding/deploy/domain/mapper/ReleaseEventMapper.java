package io.github.dekkerding.deploy.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.dekkerding.deploy.domain.entity.ReleaseEventEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReleaseEventMapper extends BaseMapper<ReleaseEventEntity> {
}
