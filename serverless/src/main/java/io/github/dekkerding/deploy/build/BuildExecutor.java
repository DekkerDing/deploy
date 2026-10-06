package io.github.dekkerding.deploy.build;

/**
 * 构建执行器 SPI（design D5）。
 *
 * 现有实现：ProcessBuildExecutor（mvn/gradle/npm 子进程）。
 * 未来实现（同一接缝）：python/go 脚本执行器、Docker 容器执行器、远程 agent。
 * 插件化 change（plugin-system）将以本接口为运行时加载的扩展点之一。
 */
public interface BuildExecutor {

    /** 本执行器是否支持该构建类型。 */
    boolean supports(BuildType type);

    /**
     * 执行构建：阻塞直到完成/失败/超时。
     * 约定：输出全部追加写入 ctx.logFile；不抛检查异常，失败以 BuildResult 返回。
     */
    BuildResult execute(BuildContext ctx);
}
