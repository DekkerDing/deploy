package io.github.dekkerding.deploy.build;

/** 构建类型（与项目注册的 build_type 对应；未来多语言执行器扩展此处）。 */
public enum BuildType {
    MAVEN,
    GRADLE,
    NPM
}
