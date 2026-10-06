## Purpose

将异构目标环境建模为维度组合（os × arch × libc × 运行载体 × 控制通道），支持探针自动发现与制品路由解析，使"选择什么制品发给什么目标"成为可独立验证的决策，未知系统无需改代码即可接入。

## ADDED Requirements

### Requirement: 目标环境建模与注册
系统 SHALL 以维度组合建模目标环境：os（开放枚举：linux/windows/darwin/...）、arch（amd64/arm64/armv7/386/...）、libc（glibc/musl，仅 linux）、运行载体（jvm/docker/k8s/原生进程）、控制通道（ssh/winrm/local），并支持手动注册与列表查询。

#### Scenario: 注册目标环境
- **WHEN** 用户登记一台 `linux/amd64`、jvm 载体、ssh 通道的目标机（地址与凭据）
- **THEN** 目标环境创建成功，可被选为交付目标

#### Scenario: 未列举的系统
- **WHEN** 目标机的 os 取值不在既有枚举内（如 freebsd）
- **THEN** 系统以新取值完成注册，路由逻辑不因新取值而需要修改

### Requirement: 探针自动发现
系统 SHALL 支持通过控制通道对目标机执行探测命令（类 Unix：`uname -sm`；Windows：处理器架构环境变量），将结果解析为平台描述符并自动登记；无法识别的取值 MUST 标记为 `UNKNOWN` 并等待人工确认，不得猜测填充。

#### Scenario: Linux 目标机探针
- **WHEN** 对一台 Linux x86-64 目标机发起探针
- **THEN** 解析 `Linux x86_64` 为 `linux/amd64` 并更新该目标环境的平台描述

#### Scenario: 未知取值
- **WHEN** 探针返回无法识别的 os/架构字符串
- **THEN** 目标环境平台描述标记为 `UNKNOWN`，交付被阻止直至人工确认

### Requirement: 制品路由解析
给定一次发布的制品集与目标环境，系统 SHALL 按序解析：先精确匹配（os+arch+libc），无匹配且存在 `PORTABLE` 制品时回退到可移植制品；两者皆无时 MUST 拒绝交付，并在错误中列出现有制品的平台清单。

#### Scenario: 精确匹配优先
- **WHEN** 目标为 `windows/amd64` 且制品集中含 `windows/amd64` 绑定制品与 `PORTABLE` JAR
- **THEN** 路由选择 `windows/amd64` 绑定制品

#### Scenario: 可移植回退
- **WHEN** 目标为 `linux/arm64` 且制品集仅含 `PORTABLE` JAR
- **THEN** 路由回退选择该 JAR

#### Scenario: 无可用制品
- **WHEN** 目标为 `windows/arm64` 且制品集仅有 `linux/amd64` 绑定制品
- **THEN** 交付被拒绝，错误信息列出所有制品的平台声明

### Requirement: 目标 JVM 版本预检
当交付 JAR 制品到 jvm 载体的目标环境时，系统 SHALL 在部署前校验制品字节码版本不高于目标环境声明的 JVM 版本；不满足时 MUST 在部署前失败并说明原因。

#### Scenario: 字节码版本高于目标 JVM
- **WHEN** 以目标 JDK8 编译的制品被交付到声明 JVM 8 的目标环境
- **THEN** 预检通过，部署继续

#### Scenario: 字节码版本超前
- **WHEN** 字节码版本为 17 的制品被交付到声明 JVM 8 的目标环境
- **THEN** 部署在执行前被拒绝，错误说明"目标 JVM 版本低于制品要求"
