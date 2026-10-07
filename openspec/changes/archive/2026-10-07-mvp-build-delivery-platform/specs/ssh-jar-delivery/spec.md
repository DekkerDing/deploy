## Purpose

首个交付模式：通过 SSH 通道将 JAR 制品部署到远程目标机并安装为系统服务，按目标操作系统适配服务管理器与路径，部署后执行健康检查并支持回滚。

## ADDED Requirements

### Requirement: SSH 通道部署
系统 SHALL 通过 SSH/SFTP 连接目标机完成：上传 JAR 制品到该操作系统的约定应用目录（类 Unix：`/opt/<项目>/`；Windows：`C:\apps\<项目>\`），保留历史版本目录。

#### Scenario: 部署到 Linux 目标
- **WHEN** 对 `linux/amd64` 目标环境执行 JAR 交付
- **THEN** 制品上传至 `/opt/<项目>/<版本>/`，旧版本文件未被删除

#### Scenario: 部署到 Windows 目标
- **WHEN** 对 `windows/amd64` 目标环境执行 JAR 交付
- **THEN** 制品上传至 `C:\apps\<项目>\<版本>\`

### Requirement: 跨 OS 服务管理适配
部署 SHALL 依据目标 os 选择服务管理方式：linux 安装/刷新 systemd 单元并重启服务；windows 以服务包装器（WinSW 或等价物）安装为 Windows 服务并重启。服务启动命令 MUST 指向新版本制品。

#### Scenario: Linux systemd 适配
- **WHEN** 交付到 linux 目标且服务不存在
- **THEN** 系统生成 systemd 单元、加载并启动服务

#### Scenario: Windows 服务适配
- **WHEN** 交付到 windows 目标
- **THEN** 应用被注册为 Windows 服务并以新制品路径重启

### Requirement: 部署后健康检查
部署动作完成后，系统 SHALL 对目标服务的端口执行探活（带重试与超时）；探活成功方将部署记录为成功，超时则标记失败并保留诊断信息。

#### Scenario: 健康检查通过
- **WHEN** 服务在重试窗口内于目标端口接受连接
- **THEN** 部署记录为成功，发布单状态推进

#### Scenario: 健康检查超时
- **WHEN** 重试窗口内端口始终不可达
- **THEN** 部署记录为失败，诊断信息包含最后一次探测错误

### Requirement: 部署回滚
对已成功部署的发布单，系统 SHALL 支持回滚到该目标环境上一次成功的制品版本：切换服务指向旧版本、重启并通过健康检查后完成回滚。

#### Scenario: 回滚到上一版本
- **WHEN** 用户对 `DEPLOYED` 状态的发布单在某目标环境发起回滚
- **THEN** 服务恢复运行上一成功版本，回滚后健康检查通过，发布单状态更新为回滚完成

### Requirement: 部署记录留痕
每次交付 MUST 生成部署记录：目标环境、所用制品及版本、开始/结束时间、结果与健康状态，可按发布单与目标环境查询。

#### Scenario: 查询部署历史
- **WHEN** 用户查询某目标环境的部署历史
- **THEN** 返回按时间排序的部署记录清单
