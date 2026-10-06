package io.github.dekkerding.deploy.build;

/**
 * 构建类型（与项目注册的 build_type 对应）。
 * 类型名是平台词汇表；实现可来自插件（如 SCRIPT 由示例插件承接，插件未加载时该类型不可用）。
 */
public enum BuildType {
    MAVEN,
    GRADLE,
    NPM,
    FLUTTER,
    /** 脚本构建（plugin-system 示例插件承接：执行项目内声明的构建脚本）。 */
    SCRIPT
}
