package io.github.dekkerding.deploy.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 发布单：一次"构建→交付"生命周期的聚合根。 */
@Data
@TableName("release")
public class ReleaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String version;

    /** ReleaseState 枚举名字符串（CREATED/BUILDING/...） */
    private String state;

    /** FAILED 时的原因摘要 */
    private String failReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
