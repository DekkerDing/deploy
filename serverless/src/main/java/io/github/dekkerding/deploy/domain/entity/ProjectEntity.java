package io.github.dekkerding.deploy.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 被部署项目的注册信息。 */
@Data
@TableName("project")
public class ProjectEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 唯一名称，重复注册被拒绝。 */
    private String name;

    /** MAVEN / GRADLE / NPM */
    private String buildType;

    /** 源码路径（本机目录） */
    private String sourcePath;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
