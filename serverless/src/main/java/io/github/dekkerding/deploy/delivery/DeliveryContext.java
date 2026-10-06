package io.github.dekkerding.deploy.delivery;

import io.github.dekkerding.deploy.domain.entity.ArtifactEntity;
import io.github.dekkerding.deploy.domain.entity.ProjectEntity;
import io.github.dekkerding.deploy.domain.entity.ReleaseEntity;
import io.github.dekkerding.deploy.domain.entity.TargetEnvEntity;
import lombok.Builder;
import lombok.Data;

/** 一次交付的完整上下文（编排层组装，SPI 只读）。 */
@Data
@Builder
public class DeliveryContext {

    private ReleaseEntity release;

    private ProjectEntity project;

    /** 路由选中的制品（PORTABLE 或精确匹配） */
    private ArtifactEntity artifact;

    private TargetEnvEntity targetEnv;

    /** 制品在平台侧的物理文件绝对路径 */
    private String localArtifactPath;

    /** 目标机安装目录（由 PathPolicy 生成，任务 6.3） */
    private String remoteInstallDir;

    /** 部署记录 id（留痕行由编排层先建后调 SPI） */
    private Long deploymentId;
}
