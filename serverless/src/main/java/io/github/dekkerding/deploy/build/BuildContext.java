package io.github.dekkerding.deploy.build;

import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;

/** 一次构建执行的上下文（由编排层组装，执行器只读）。 */
@Data
@Builder
public class BuildContext {

    private Long releaseId;

    private Long projectId;

    private String projectName;

    private BuildType buildType;

    /** 项目源码目录（构建的工作目录） */
    private Path sourcePath;

    /** 本次构建日志文件（执行器负责将输出流写入此文件） */
    private Path logFile;

    /** 构建超时毫秒数（<=0 表示不限） */
    private long timeoutMillis;
}
