package io.github.dekkerding.deploy.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务实例（specs/instance-scaling / design D4）：
 * 同一发布单在同一目标环境的多实例——独立端口 + 独立服务单元。
 * 端口 = env.basePort + seq - 1；旧单实例部署视为 seq=1（向后兼容）。
 */
@Data
@TableName("service_instance")
public class InstanceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long targetEnvId;

    /** 该实例当前运行的发布单 */
    private Long releaseId;

    private Long artifactId;

    /** 实例编号 1..N（同环境唯一） */
    private Integer seq;

    /** 监听端口（= env.basePort + seq - 1） */
    private Integer port;

    /** RUNNING / STOPPED */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
