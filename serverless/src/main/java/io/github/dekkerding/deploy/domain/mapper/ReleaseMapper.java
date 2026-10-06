package io.github.dekkerding.deploy.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.dekkerding.deploy.domain.entity.ReleaseEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReleaseMapper extends BaseMapper<ReleaseEntity> {
}
