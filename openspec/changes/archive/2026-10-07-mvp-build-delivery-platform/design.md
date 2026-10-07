## Context

仓库现状：`serverless/` 为 Spring Initializr 脚手架（Spring Boot 2.6.14 / Java 8），仅含演示 Controller；`build.gradle` 已引入 fabric8、docker-java（本次不用，保留）；`bootstrap.yaml` 内有被注释的 MyBatis-Plus + 动态数据源配置。工程基座固定 JDK8 + Boot 2.6.14，不升级。运行环境为两台实体电脑，无服务器、无外部数据库。动机见 proposal.md。

## Goals / Non-Goals

**Goals:**
- 在单模块内清晰分层（api / domain / infra / build / delivery），为未来 Provider 与多语言执行器留接缝
- 所有跨 OS 差异收敛到适配器（服务管理器、路径、命令 shell），核心流程 OS 无关
- 路由解析实现为无状态纯函数，可独立单测
- 平台重启不丢状态：发布单状态、日志、制品全部落盘/落库

**Non-Goals（设计层面明确排除）:**
- 不做分布式/多节点平台自身部署
- 不做凭据加密存储（标记为已知风险，见 Risks）
- 不做实时日志推流（MVP 轮询增量，WebSocket 留待 UI 阶段）

## Decisions

### D1. 模块与包重组：目录不动，包名与工程名先行
- `settings.gradle` 的 `rootProject.name` 改为 `deploy-platform`；代码包从 `io.github.dekkerding.serverless` 重命名为 `io.github.dekkerding.deploy`；`serverless/` 目录名暂不改（避免 IDE/路径大迁移），后续独立 change 处理
- 分层：`api`（REST）、`domain`（聚合+状态机+路由）、`infra`（存储/SSH/进程）、`build`（BuildExecutor SPI）、`delivery`（DeliveryProvider SPI + OS 适配器）
- 备选：立即重命名目录——被否，改动面大且无行为收益

### D2. 持久层：MyBatis-Plus + H2（file mode）
- 沿用原注释计划的 MyBatis-Plus（用户生态熟悉、迁 MySQL 只换连接配置），H2 file mode 满足"零安装持久化"
- 表：`project`、`release`（含 state）、`release_event`（时间线）、`artifact`（含 platform 字段与 portability）、`target_env`（维度列）、`deployment`
- 备选：Spring Data JPA——被否，与既有 MyBatis-Plus 计划割裂

### D3. SSH 客户端：sshj
- `com.hierynomus:sshj`：API 简洁，auth/sftp/exec 一套齐全，Java 8 兼容
- 备选：Apache MINA sshd——功能等价但 API 偏重；如遇坑可平替（仅 infra 层受影响）

### D4. 状态机：枚举 + 显式转移表
- `ReleaseState` 枚举内建 `Map<State, Set<State>>` 合法转移，`transition()` 非法即抛领域异常；每次转移追加 `release_event`
- 备选：Spring StateMachine——被否，重量级且状态集很小

### D5. 构建执行：线程池 + 进程句柄管理
- `BuildExecutor` SPI：`boolean supports(BuildType)` / `BuildResult execute(BuildContext)`
- 首个实现 `ProcessBuildExecutor`：`ProcessBuilder` 起子进程，stdout/stderr 合并流式写入 `logs/{releaseId}/build.log`；`ExecutorService`（固定线程数 = 并发上限）+ 有界等待队列实现排队；超时 `destroyForcibly()`
- 未来 python/go 执行器与远程 agent 实现同一 SPI，不改编排层

### D6. 路由器：无状态纯函数
- `RoutingResolver.resolve(List<ArtifactMeta>, TargetEnv, DeliveryRequest) → ResolvedArtifact | RoutingError`
- 规则序：精确匹配（os+arch+libc）→ `PORTABLE` 回退 → 报错（附制品平台清单）；JVM 字节码版本取 JAR 的 major version（52=JDK8）对比目标声明
- 未知 os/arch：`UNKNOWN` 哨兵值，路由器遇到即拒绝——不猜测

### D7. 跨 OS 交付适配：两个策略接口
- `ServiceManagerAdapter`：`install/restart/stop`；实现 `SystemdAdapter`（生成 unit 文件、`systemctl daemon-reload`）、`WinSwAdapter`（生成 WinSW XML、`sc`/net 服务命令）
- `PathPolicy`：按 os 计算应用目录/版本目录/日志目录
- SSH 远程命令按 os 显式选择 shell（linux: `bash -lc`；windows: `cmd /c` 或 `powershell -Command`），避免依赖 sshd 默认 shell

### D8. Windows 目标机环境预置
- Windows 目标需一次性预置：JRE 目录 + WinSW 可执行文件；平台提供 `env-prepare` 脚本（经 SSH 推送并执行）完成安装，MVP 允许手工预置
- 已知边界（探索期核实）：windows/arm64 无原生 JDK8——该组合下 JAR 交付要求目标 JVM ≥11 或 x64 模拟，由 D6 的 JVM 预检在部署前拦截

### D9. 自测目标：电脑 A 自举
- 电脑 A（本机 Windows）同时作为平台与首个交付目标（SSH localhost → 127.0.0.1，需开启 OpenSSH Server 可选功能）；用 A 机上运行的第二个端口模拟"被部署服务"
- 电脑 B 确认系统后，仅注册新 TargetEnv 即接入，不改代码

### D10. 凭据与安全（MVP 级）
- SSH 凭据（密码/私钥）存于 H2，明文；API 无鉴权，仅监听内网
- 标记为已知风险，服务器化之前必须独立 change 处理（加密存储 + API 鉴权）

## Risks / Trade-offs

- [Windows sshd 默认 shell 差异导致命令行为不一致] → D7：所有远程命令显式指定 shell，命令模板单测覆盖
- [H2 文件库并发写冲突] → 平台单机、构建并发低（≤4），H2 默认锁足够；预留 MySQL 迁移路径
- [凭据明文 + API 无鉴权] → D10 明示为 MVP 边界；服务器化前强制安全 change
- [WinSW/JRE 预置失败使 Windows 交付不可用] → D8 环境预置脚本 + 明确的预检错误信息
- [构建子进程僵死] → 超时 destroyForcibly + 发布单 FAILED 兜底
- [JDK8 语法约束] → 全部代码 Java 8 语法，CI 无（本地 gradlew build 自证）

## Migration Plan

绿地功能，无存量迁移。回滚策略 = git revert（平台自身代码）+ H2 数据文件可整体删除重建（storage/ 与 logs/ 为纯文件，可备份）。平台迁服务器的路径：H2 → MySQL（换配置）+ 平台 jar 以 systemd/WinSW 自托管（吃自己的狗粮）。

## Open Questions

- 电脑 B 的操作系统与 OpenSSH 可用性——不阻塞（D9 以 A 机自举验证），确认后注册即可
- `serverless/` 目录重命名时机——独立 change，不影响本次任务
