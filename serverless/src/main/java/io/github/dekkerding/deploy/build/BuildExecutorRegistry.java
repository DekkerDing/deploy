package io.github.dekkerding.deploy.build;

import io.github.dekkerding.deploy.build.BuildExecutor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 构建执行器注册表：按构建类型路由（多实现并存，含未来插件实现）。 */
@Component
public class BuildExecutorRegistry {

    private final List<BuildExecutor> executors;

    public BuildExecutorRegistry(List<BuildExecutor> executors) {
        this.executors = executors;
    }

    /** 找到支持该类型的执行器；找不到抛出明确异常（如命令缺失场景由执行器内部报告）。 */
    public BuildExecutor resolve(BuildType type) {
        for (BuildExecutor e : executors) {
            if (e.supports(type)) {
                return e;
            }
        }
        throw new IllegalArgumentException("没有支持该构建类型的执行器: " + type);
    }
}
