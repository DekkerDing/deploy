## Context

MVP 后端（JDK8 + Spring Boot 2.6.14，`serverless/`）已具备完整 API：项目/发布单/日志流（字节偏移增量）/制品（平台描述符+sha256）/目标环境（五维开放建模）/SSH 交付（WinSW/systemd）/健康检查（TCP 重试窗口）/回滚/部署历史。本变更加入前端控制台与实例扩容。环境：两台电脑（A=平台，B=目标），本机 Flutter 3.47.4 已装（清华镜像 stable）。

**修订（2026-10-06）**：用户已自建双前端（`frontend-web/` React Web + `frontend/` Flutter APP）并打通 Gradle 嵌入链与 SPA fallback——D1/D2/D5/D6/D7 按现实修订，原"单 Flutter 工程双产物"方案作废（其 Web 产物线由 React 承担）。

## Goals / Non-Goals

**Goals**
- 单 Flutter 工程双产物（Web 嵌 jar + APK 入制品库），monorepo 布局
- 认证最小可用（单令牌 + filter，可关闭保持 curl 兼容）
- 实例扩缩容模型落在既有交付管线之上（复用 ServiceManager/HealthChecker，不重造）
- 开发期体验：代理脚本解决 flutter run 与后端跨端口

**Non-Goals**
- 不做多用户/角色/权限体系（D10 边界，服务器化前升级）
- 不做流量负载均衡（TCP 代理/KEDA/Knative 留给后续"流量策略"维度变更）
- 不做 iOS/鸿蒙产物（模型支持，产物按需开启）
- 不改 JDK8 基座与既有 API 契约（新增不破坏）

## Decisions

### D1: monorepo 布局 —— `frontend-web/` + `frontend/` 与 `serverless/` 平级（修订：双前端）
```
deploy/
  frontend-web/       # React 工程（react 18 + vite 5 + ts + tailwind + zustand + axios）→ Web 控制台，dist 嵌入 jar
  frontend/           # Flutter APP 工程（MVVM + Provider + go_router + features/{域}/，参照 xhbx 骨架）→ Android 移动端
  serverless/         # 既有后端（JDK8）；WebMvcConfig 提供 SPA 路由 fallback
```
原方案：单 Flutter 工程 `console/` 双产物 —— 作废（用户选择 React 承担 Web 线）。否决理由补记：Flutter Web 首屏体积与加载在低配运维终端场景不占优，React 生态与构建速度更合适；Flutter 专注 APP。

### D2: Web 产物嵌入路径 —— Gradle 任务链拷贝（已实现收编）
`frontend-web` 产物（`dist/`）由 `serverless/build.gradle` 任务链嵌入：`reactInstall`（npm install）→ `reactBuild`（vite build）→ `cleanStatic` → `copyReactAssets`（拷入 `build/resources/main/static/`）→ `processResources` 挂接 → `bootJar` 出单体 jar。产物落 `build/` 目录（不入 `src/main/resources/`，天然不入库）。SPA 路由由 `WebMvcConfig` 的 PathResourceResolver fallback（非静态路径回 index.html）支撑。备选：flutter build web 产物嵌入 —— 作废（见 D1）；zip 制品 + 目标机解压 —— 否决：违背"一个项目一个交付物"语义。

### D3: 认证 —— 内存会话 Map + 配置令牌 + HandlerInterceptor
`deploy.auth.token`（空=关闭）。登录端点比对后发随机会话 id（内存 Map，TTL 12h，重启失效可接受）。拦截器白名单：`/api/auth/login`、`/`（静态资源）、`/h2-console/**`。备选：JWT —— 否决：JDK8 基座下引依赖不值，单用户场景内存会话足够。

### D4: 实例模型 —— instance 表 + 端口算术 + 服务名后缀
```
instance: id, target_env_id, release_id, artifact_id, seq(1..N), port, status(RUNNING/STOPPED), created_at
```
端口 = env.basePort + seq - 1。服务名：linux `app@N.service`（systemd 模板单元，%i=实例号）；windows `app-N`（WinSW 服务 id 加后缀）。**ServiceManager 接口扩展**：`instanceServiceId(ctx, seq)`、定义/命令生成带实例参数——保持"适配器产内容、通道执行"的既有分层。扩容=对新 seq 逐个走 deliver 流程（端口注入）；缩容=对尾部 seq 逐个 deactivate。旧单实例部署视为 seq=1，向后兼容。

### D5: FLUTTER 构建类型 —— ProcessBuildExecutor 扩展（修订：聚焦 APK 产物）
`BuildType.FLUTTER`：命令序列 `flutter pub get` → `flutter build apk --release`。产物发现规则：`build/app/outputs/flutter-apk/*.apk` → android 平台绑定制品（arch 默认 arm64）。构建对象即 `frontend/` APP 工程（或任意 Flutter 工程）。原方案中"flutter build web 嵌 jar"职责已由 React 线（D2）承担，FLUTTER 类型不再产出 web 形态；单体 jar 自举走 React + Gradle。

### D6: 前端架构 —— 双栈分治（修订）
- **Web（frontend-web/）**：React 18 函数组件 + zustand（`stores/{域}`）+ axios 封装（`core/api.ts` 拦截器 → ApiException）；路由 react-router-dom；样式 Tailwind；日志窗等页面局部状态用组件内 state（不进全局 store）
- **APP（frontend/）**：Flutter MVVM + Provider + go_router（登录守卫）；`services/http_client.dart` 统一 BASE_URL（AppConfig 可配）+ 错误码映射
- 两端共享同一 API 契约与页面域划分（dashboard/project/release/target_env/artifact）
- 日志滚动两端同语义：1s 轮询 `?offset=nextOffset` 续读，release state 终态停止——后端增量 API 语义零适配

### D7: 开发期联调 —— vite proxy（已实现收编）
Web：vite dev server（3000）`/api` 代理到 8080（`vite.config.ts`）；生产同源（`core/config.ts` 取 window.location）。APP：平台地址在 `AppConfig` 配置。原方案 start-dev.bat 双拉起脚本与后端 dev CORS 均不再需要。

## Risks / Trade-offs

- **Flutter 工具链进入构建路径**（仅 APK/FLUTTER 构建线；React 嵌入线只依赖 Node）：构建机必须装 Flutter —— 两台电脑已具备；`flutter` 缺失时构建失败消息已按既有语义指明
- **WinSW 多服务实例**：每实例一个服务注册，缩容漏清理会留脏服务 —— 缩容幂等（注销前先 stop，失败告警不阻断）+ 实例表与实际服务对账可后续补
- **static/ 嵌入使 jar 体积 +2-5MB**：可接受
- **认证令牌明文配置**：D10 边界内，服务器化时升级
- **systemd 模板单元在 Win 目标无对应物**：两平台各自适配，行为差异（模板 vs 多服务）封装在 ServiceManager 实现内，编排层只见 seq
