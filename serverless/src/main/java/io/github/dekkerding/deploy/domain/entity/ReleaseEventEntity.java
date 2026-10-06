package io.github.dekkerding.deploy.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 发布单状态流转的时间线事件，每次 transition 追加一条。 */
@Data
@TableName("release_event")
public class ReleaseEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long releaseId;

    private String fromState;

    private String toState;

    private String message;

    private LocalDateTime createdAt;
}
