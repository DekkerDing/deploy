package io.github.dekkerding.deploy.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 交付制品：构建产物入库记录。
 * 平台描述符：portable=true 时平台三字段为空（可路由到任意 os/arch）；
 * 否则 platform_os/arch/libc 声明绑定平台，路由仅精确匹配。
 */
@Data
@TableName("artifact")
public class ArtifactEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long releaseId;

    private Long projectId;

    private String fileName;

    /** 相对 storage 根目录的路径 */
    private String storagePath;

    private Long sizeBytes;

    /** 小写十六进制 sha256，下载校验依据 */
    private String sha256;

    private String platformOs;

    private String platformArch;

    private String platformLibc;

    private Boolean portable;

    private LocalDateTime createdAt;
}
