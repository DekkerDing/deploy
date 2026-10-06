package io.github.dekkerding.deploy.build;

import lombok.Data;

/** 构建执行结果。 */
@Data
public class BuildResult {

    private final boolean success;

    private final int exitCode;

    private final String message;

    /** 构建产物候选（如 target/*.jar），由执行器报告，制品入库阶段消费 */
    private final java.util.List<java.nio.file.Path> producedFiles;

    private BuildResult(boolean success, int exitCode, String message, java.util.List<java.nio.file.Path> producedFiles) {
        this.success = success;
        this.exitCode = exitCode;
        this.message = message;
        this.producedFiles = producedFiles == null
                ? java.util.Collections.emptyList()
                : java.util.Collections.unmodifiableList(producedFiles);
    }

    public static BuildResult ok(int exitCode, java.util.List<java.nio.file.Path> producedFiles) {
        return new BuildResult(true, exitCode, "OK", producedFiles);
    }

    public static BuildResult fail(int exitCode, String message) {
        return new BuildResult(false, exitCode, message, null);
    }
}
