package io.github.dekkerding.deploy.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 部署记录：一次向目标环境交付的留痕。 */
@Data
@TableName("deployment")
public class DeploymentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long releaseId;

    private Long artifactId;

    private Long targetEnvId;

    /** SUCCESS / FAILED / ROLLED_BACK */
    private String result;

    private String message;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
