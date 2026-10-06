package io.github.dekkerding.deploy.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 目标环境：五维建模（os × arch × libc × 运行载体 × 控制通道）。
 * os/arch 为开放取值（未列举系统零代码接入）；探针无法识别时 probe_status=UNKNOWN，
 * 交付被阻止直至人工确认（specs/delivery-routing）。
 */
@Data
@TableName("target_env")
public class TargetEnvEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** linux / windows / darwin / ...（开放枚举，规范名对齐 go tool dist list） */
    private String os;

    /** amd64 / arm64 / armv7 / 386 / ... */
    private String arch;

    /** glibc / musl（仅 linux 有意义） */
    private String libc;

    /** JVM / DOCKER / K8S / NATIVE */
    private String runtimeType;

    /** SSH / WINRM / LOCAL */
    private String reach;

    private String host;

    private Integer port;

    private String username;

    /** MVP 明文（design D10 声明的安全边界，服务器化前必须治理） */
    private String credential;

    /** runtime_type=JVM 时目标 JVM 大版本（如 8/11/17），供字节码预检 */
    private Integer jvmVersion;

    /** KNOWN / UNKNOWN */
    private String probeStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
