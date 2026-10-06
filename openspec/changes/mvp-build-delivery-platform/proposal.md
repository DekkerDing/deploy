# MVP：构建-交付平台（首个端到端闭环）

## Why

本仓库定位为"自动化运维平台"，但目前只有一个空壳：`serverless/` 模块仅含 Spring Initializr 演示代码，无任何业务能力。用户需要一套类 Jenkins 的构建/打包/发布/部署平台，核心差异化是**交付模式可选**（运行载体 × 交付物形态 × 流量策略），并且目标环境异构（linux/windows × amd64/arm64，含未列举系统）。当前无服务器，仅两台实体电脑（A=平台宿主，B=交付目标），需要先以最小代价跑通"构建 → 制品 → 路由 → 交付"闭环，后期再迁服务器与扩展 Serverless 交付。

## What Changes

- **仓库整顿**：合并 `serverless/` 嵌套 git 仓库入外层、恢复根 `.gitignore`、删除演示代码、包重命名为平台命名空间、`bootstrap.yaml` 改为 `application.yaml`
- **领域模型**：引入 Project（项目）/ Release（发布单）/ Artifact（制品）/ Deployment（部署记录）/ TargetEnv（目标环境）聚合，发布单状态机 `CREATED → BUILDING → BUILT → DEPLOYING → DEPLOYED / FAILED`（支持 ROLLED_BACK）
- **存储**：H2（file 模式）零安装持久化，迁服务器时可平滑切换 MySQL
- **REST API**：项目注册、发布单创建/触发/查询、制品列表/下载、目标环境注册与探针
- **构建执行**：`BuildExecutor` SPI + 首个 `ProcessBuildExecutor`（ProcessBuilder 调 mvn/gradle/npm），构建日志落盘 + 增量轮询，并发构建互不干扰——此即"多实例构建"的第一形态，未来 python/go 进程间调用挂接同一 SPI
- **制品管理**：制品落盘 `storage/{project}/{version}/` + 元数据入库；每制品携带平台描述符（os/arch/libc）与可移植性标志（PORTABLE / PLATFORM_BOUND）
- **交付路由**：`RoutingResolver` 制品路由（精确匹配 → PORTABLE 回退 → 明确报错并列出可用项）+ JAR 目标 JVM 版本预检；目标环境探针（`uname -sm` / `%PROCESSOR_ARCHITECTURE%`）自动注册，未知系统标记 UNKNOWN 待人工确认
- **首个交付 Provider**：`SshJarProvider`——scp/sftp 上传 + ssh 远程重启 + 端口探活；按目标 OS 选择服务管理器适配器（linux → systemd unit；windows → WinSW 包装 Windows 服务）与路径策略（`/opt/app` vs `C:\apps\app`）
- **明确不做（本次范围外）**：Docker/K8s Provider、Serverless 交付、Web UI、多租户、WinRM 通道、Go/Python worker、多架构交叉编译矩阵——均已在架构上留好接缝

## Capabilities

### New Capabilities

- `release-management`：项目注册、发布单生命周期与状态机、触发与进度查询的 REST API 行为
- `build-execution`：构建执行 SPI、进程式构建（mvn/gradle/npm）、日志持久化与增量获取、并发构建隔离
- `artifact-management`：制品存储、平台描述符与可移植性声明、制品列表与下载
- `delivery-routing`：TargetEnv 建模（os/arch/libc/runtime/reach 维度）、探针自动发现、制品路由与回退规则、JVM 版本预检
- `ssh-jar-delivery`：SSH+JAR 交付 Provider 的部署、健康检查、回滚行为及跨 OS 服务管理器适配

### Modified Capabilities

（无——仓库尚无既有规格）

## Impact

- **代码**：`serverless/` 整体重组（包名 `io.github.dekkerding.serverless` → 平台命名空间；删除 `demos/web`）；`ServerlessApplication` 保留为启动入口
- **构建配置**：`build.gradle` 新增依赖——H2、持久层（MyBatis-Plus 或 Spring Data JPA，择一于 design 决定）、SSH 客户端库（Apache MINA sshd 或 sshj，择一于 design 决定）；既有 fabric8/docker-java 保留（未来 Provider 用）
- **配置**：`bootstrap.yaml` → `application.yaml`，新增数据源/存储目录/SSH 配置段
- **兼容性**：绿地项目，无破坏性变更；JDK8 + Spring Boot 2.6.14 基座保持不变
- **假设记录**：电脑 B 的操作系统尚未确认——MVP 自测以电脑 A 自身为交付目标（SSH localhost）验证 linux/windows 两个适配器路径；B 机确认后仅需注册为目标环境，无需改代码
