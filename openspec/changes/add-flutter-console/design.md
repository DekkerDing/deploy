## Context

MVP 后端（JDK8 + Spring Boot 2.6.14，`serverless/`）已具备完整 API：项目/发布单/日志流（字节偏移增量）/制品（平台描述符+sha256）/目标环境（五维开放建模）/SSH 交付（WinSW/systemd）/健康检查（TCP 重试窗口）/回滚/部署历史。本变更加入 Flutter 控制台（参照 F:\workspace\xhbx-pl-flutter：MVVM + Provider + go_router + features/{域}/）与实例扩容。环境：两台电脑（A=平台，B=目标），本机 Flutter 3.47.4 已装（清华镜像 stable）。

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

### D1: monorepo 布局 —— `console/` 与 `serverless/` 平级
```
deploy/
  console/            # Flutter 工程（web + android 同码多编）
    lib/{app.dart, main.dart, config/, core/, features/, services/, viewmodels/, widgets/}
    scripts/start-dev.bat   # 开发代理启动（参照 xhbx 模式）
  serverless/         # 既有后端（JDK8）
    src/main/resources/static/   # flutter build web 产物嵌入点（构建时生成，gitignore）
```
备选：console 放 serverless 内 —— 否决：工具链独立（Dart vs Gradle），平级最干净。

### D2: Web 产物嵌入路径 —— 构建后拷贝，gitignore static/
FLUTTER 构建流程：`flutter build web` → 产物拷贝到 `serverless/src/main/resources/static/` → 后端 `bootJar` 打出单体 jar。`static/` 目录 gitignore（产物不入库，平台构建即再生成）。备选：zip 制品 + 目标机解压 —— 否决：违背"一个项目一个交付物"语义，且需要新交付能力。

### D3: 认证 —— 内存会话 Map + 配置令牌 + HandlerInterceptor
`deploy.auth.token`（空=关闭）。登录端点比对后发随机会话 id（内存 Map，TTL 12h，重启失效可接受）。拦截器白名单：`/api/auth/login`、`/`（静态资源）、`/h2-console/**`。备选：JWT —— 否决：JDK8 基座下引依赖不值，单用户场景内存会话足够。

### D4: 实例模型 —— instance 表 + 端口算术 + 服务名后缀
```
instance: id, target_env_id, release_id, artifact_id, seq(1..N), port, status(RUNNING/STOPPED), created_at
```
端口 = env.basePort + seq - 1。服务名：linux `app@N.service`（systemd 模板单元，%i=实例号）；windows `app-N`（WinSW 服务 id 加后缀）。**ServiceManager 接口扩展**：`instanceServiceId(ctx, seq)`、定义/命令生成带实例参数——保持"适配器产内容、通道执行"的既有分层。扩容=对新 seq 逐个走 deliver 流程（端口注入）；缩容=对尾部 seq 逐个 deactivate。旧单实例部署视为 seq=1，向后兼容。

### D5: FLUTTER 构建类型 —— ProcessBuildExecutor 扩展
`BuildType.FLUTTER`：命令序列 `flutter pub get` → `flutter build web --release` →（按配置）`flutter build apk --release`。产物发现规则扩展：`build/web/` → 嵌 static/（全栈路径，项目配置 `frontend.embed=true` 时）；`build/app/outputs/flutter-apk/*.apk` → android 平台绑定制品。单体 jar 的组装：web 产物拷贝后触发后端工程 `bootJar`，jar 即制品——FLUTTER 项目的"后端工程"指向 `serverless/` 自身（自举）或任意 JVM 工程。

### D6: 前端架构 —— 照搬 xhbx 骨架不搬代码
- 状态：Provider + ChangeNotifier；路由：go_router（登录守卫 → /dashboard）
- `services/http_client.dart`：统一 BASE_URL（Web=同源 `''`，APP=设置页存储的地址）+ 令牌头 + 错误码映射
- features/ 域：auth、dashboard、projects、releases（含日志滚动轮询 Timer + offset 续读）、artifacts、envs、deployments、scaling
- 日志滚动：1s 轮询 `?offset=nextOffset`，state 终态停止——后端增量 API 语义零适配

### D7: 开发期代理 —— console/scripts/start-dev.bat
参照 xhbx：脚本起后端(8080) + `flutter run -d chrome --web-port 8090`，dart 侧开发配置走 `http://127.0.0.1:8080` 直连（后端 CORS filter 放行开发端口）。备选：node 代理 —— 否决：多一层依赖，Spring 一行 CORS 配置即可（仅 dev profile 开启）。

## Risks / Trade-offs

- **Flutter 工具链进入构建路径**：构建机必须装 Flutter —— 两台电脑已具备；`flutter` 缺失时构建失败消息已按既有语义指明
- **WinSW 多服务实例**：每实例一个服务注册，缩容漏清理会留脏服务 —— 缩容幂等（注销前先 stop，失败告警不阻断）+ 实例表与实际服务对账可后续补
- **static/ 嵌入使 jar 体积 +2-5MB**：可接受
- **认证令牌明文配置**：D10 边界内，服务器化时升级
- **systemd 模板单元在 Win 目标无对应物**：两平台各自适配，行为差异（模板 vs 多服务）封装在 ServiceManager 实现内，编排层只见 seq
