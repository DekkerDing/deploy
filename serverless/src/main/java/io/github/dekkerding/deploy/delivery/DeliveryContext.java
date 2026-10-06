package io.github.dekkerding.deploy.delivery;

import io.github.dekkerding.deploy.domain.entity.ArtifactEntity;
import io.github.dekkerding.deploy.domain.entity.ProjectEntity;
import io.github.dekkerding.deploy.domain.entity.ReleaseEntity;
import io.github.dekkerding.deploy.domain.entity.TargetEnvEntity;
import lombok.Builder;
import lombok.Data;

/** 一次交付的完整上下文（编排层组装，SPI 只读）。 */
@Data
@Builder(toBuilder = true)
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

    // ---- 多实例（specs/instance-scaling / design D4；空 = 单实例默认语义，向后兼容） ----

    /** 实例编号 1..N：非空时服务名带实例后缀（systemd app@N / WinSW app-N） */
    private Integer instanceSeq;

    /** 实例监听端口（= env.basePort + seq - 1）：非空时注入服务启动参数并用于实例级探活 */
    private Integer instancePort;

    /**
     * 跳过制品上传（扩缩容专用）：制品已在共享版本目录且可能被运行中实例的进程锁定
     * （Windows SFTP 覆盖必失败），扩缩容仅上传实例独立的服务定义/WinSW exe。
     */
    private Boolean skipArtifactUpload;
}
