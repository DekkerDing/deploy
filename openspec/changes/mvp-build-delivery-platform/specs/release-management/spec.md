## Purpose

管理被部署项目与发布单的全生命周期：项目注册、发布单创建、触发、状态流转与进度查询，是平台其余能力（构建、制品、交付）的编排入口。

## ADDED Requirements

### Requirement: 项目注册与查询
系统 SHALL 支持注册待部署项目，登记其名称、构建类型（maven/gradle/npm）及构建上下文（源码路径或仓库地址），并支持列出与查询单个项目。

#### Scenario: 注册新项目
- **WHEN** 用户提交项目名称 `demo-app`、构建类型 `maven`、源码路径
- **THEN** 系统创建该项目并返回唯一项目标识，可通过列表与详情接口再次查询到

#### Scenario: 注册重复名称项目
- **WHEN** 用户以已存在的名称再次注册项目
- **THEN** 系统拒绝并返回明确的冲突错误，原项目信息不变

### Requirement: 发布单创建
系统 SHALL 支持为任一已注册项目创建发布单；发布单创建后处于 `CREATED` 状态，携带版本标识。

#### Scenario: 创建发布单
- **WHEN** 用户对项目 `demo-app` 发起发布单创建
- **THEN** 系统生成发布单，状态为 `CREATED`，并返回发布单标识与版本号

### Requirement: 发布单状态机
系统 SHALL 按以下状态机管理发布单：`CREATED → BUILDING → BUILT → DEPLOYING → DEPLOYED`，任意执行态可转入 `FAILED`，`DEPLOYED` 可转入 `ROLLED_BACK`。非法状态跳转 MUST 被拒绝并返回错误。

#### Scenario: 合法流转
- **WHEN** 发布单处于 `BUILT`，对其发起向指定目标环境的交付
- **THEN** 发布单转入 `DEPLOYING`，交付成功后转入 `DEPLOYED`

#### Scenario: 非法流转被拒绝
- **WHEN** 发布单处于 `CREATED`（尚未构建）即发起交付
- **THEN** 系统拒绝该操作并返回"当前状态不允许交付"的错误，状态保持不变

#### Scenario: 构建失败
- **WHEN** 发布单处于 `BUILDING` 且构建进程以非零退出码结束
- **THEN** 发布单转入 `FAILED`，失败原因可在发布单详情中查询

### Requirement: 发布进度查询
系统 SHALL 提供发布单详情查询，包含当前状态、关联制品清单、各目标环境的部署记录及构建/交付日志的引用。

#### Scenario: 查询进行中的发布单
- **WHEN** 发布单处于 `BUILDING` 时用户查询详情
- **THEN** 返回当前状态、已产生的时间线事件，以及日志获取入口
