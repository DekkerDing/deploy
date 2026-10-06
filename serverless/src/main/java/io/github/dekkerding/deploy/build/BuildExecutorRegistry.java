package io.github.dekkerding.deploy.build;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 构建执行器注册表：按构建类型路由（多实现并存，含未来插件实现）。 */
@Component
public class BuildExecutorRegistry {

    /** 内建构建类型词汇表（enum）；插件为其中类型提供实现（如 SCRIPT 由示例插件承接）。 */
    private static final BuildType[] KNOWN_TYPES = BuildType.values();

    private final List<BuildExecutor> executors;

    public BuildExecutorRegistry(List<BuildExecutor> executors) {
        // 可变副本：插件加载后经 register() 编程式追加（design D5，非 Spring Bean 路径）
        this.executors = new ArrayList<>(executors);
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

    /** 编程式注册（插件加载器调用）：追加执行器并立即可被 resolve 命中。 */
    public void register(BuildExecutor executor) {
        executors.add(executor);
    }

    /** 当前有执行器承接的构建类型名集合（API 校验与枚举数据源，插件贡献自然出现）。 */
    public Set<String> supportedTypeNames() {
        Set<String> names = new LinkedHashSet<>();
        for (BuildType t : KNOWN_TYPES) {
            for (BuildExecutor e : executors) {
                if (e.supports(t)) {
                    names.add(t.name());
                    break;
                }
            }
        }
        return names;
    }
}
