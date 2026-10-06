## Why

平台 MVP（mvp-build-delivery-platform，32/32 完成）目前是纯 API 形态，所有操作依赖 curl 驱动，无可视化界面。用户要求加入前端页面与 Flutter APP（参照 F:\workspace\xhbx-pl-flutter 的 MVVM/Provider/go_router/features 架构），并希望前后端同仓、部署"serverless"（单体交付物）时可快速拉起并动态扩容实例。后端 API 面（项目/发布单/日志流/制品/目标环境/部署/回滚/历史）已完备，前端原料齐备； Flutter Web 产物嵌入 Spring Boot jar 即可形成"前后端一体"的单体全栈交付物，直接复用现有 JVM 交付管线。

**规划修订（2026-10-06）**：规划后用户已自建双前端工程——`frontend-web/`（React 18 + TS + Vite + Tailwind + zustand，Web 控制台）与 `frontend/`（Flutter APP，参照 xhbx 骨架），并打通 Gradle 嵌入链（React dist → 单体 jar）与 SPA fallback。本变更吸收该成果（原"单 Flutter 工程双产物"方案作废），在其上继续补齐构建日志滚动窗、认证、FLUTTER 构建类型与实例扩容。

## What Changes

- 双前端工程与后端构成 monorepo（用户已自建）：`frontend-web/` React 工程出 Web 控制台（产物嵌入单体 jar），`frontend/` Flutter 工程（MVVM + Provider + go_router + features/{域}/，参照 xhbx-pl-flutter）出 Android APP
- React 构建产物经 Gradle 任务链（reactInstall→reactBuild→cleanStatic→copyReactAssets→processResources）嵌入 `build/resources/main/static/`，`bootJar` 产出**全栈单体 jar**（页面 + API 同源同进程，无 CORS）——已实现收编
- 后端新增最小 token 认证（登录端点 + API filter + 页面登录页），替换当前零鉴权裸奔状态
- 构建能力新增 `FLUTTER` 构建类型（flutter build web/apk 子进程执行），APK 作为平台绑定制品（android）入库
- 部署能力新增**实例扩容**：同一发布单在同一目标环境可拉起 N 个实例（端口 basePort+i），linux 走 systemd 模板单元 `app@N.service`，windows 走 WinSW 多服务（`app-N`），每实例独立健康检查；实例数可增可减（缩容=停多余实例）
- 开发期体验：Web 端 vite dev proxy（3000 端口 `/api`→8080，已实现）；APP 端平台地址经 AppConfig 可配置

## Capabilities

### New Capabilities

- `console-frontend`: Web 控制台（React）与移动 APP（Flutter）双端：页面域覆盖、API 客户端与登录态、构建日志滚动窗、状态机时间线可视化
- `console-auth`: 最小认证（单 token 登录、API 过滤器、免鉴关白名单）
- `flutter-build`: FLUTTER 构建类型与全栈单体打包（BuildType 扩展、web 产物嵌入 static/、APK 平台绑定入库、产物发现规则）
- `instance-scaling`: 实例扩缩容（实例模型与端口分配、systemd 模板单元/WinSW 多服务、实例级健康矩阵、扩缩容 API）

### Modified Capabilities

（主规格库尚未建立——mvp-build-delivery-platform 未归档，其 build-execution/ssh-jar-delivery 能力仍在变更目录内。`flutter-build` 与 `instance-scaling` 对构建类型枚举和交付流程的扩展在归档时与上述能力合并为最终主规格。）

## Impact

- **代码**: 双前端工程已就位（`frontend-web/` React 18 + TS + Vite；`frontend/` Flutter 3.47）；`serverless` 侧后续新增认证 filter、FLUTTER 构建执行与实例编排（static 嵌入已实现）
- **依赖**: 后端零新依赖（认证/扩容用 JDK8 现有能力）；Web 端 react/zustand/axios，APP 端 provider/go_router/http
- **迁移**: 已有 API 行为不变（新增 X-Api-Token 头校验对 curl 用法是 **BREAKING**——提供配置开关，关闭时保持零鉴权兼容现有脚本）
- **性能**: 单体 jar 体积增长（+静态资源 ~2-5MB）；多实例端口错开无冲突
- **风险**: FLUTTER/APK 构建线需 Flutter 工具链（构建机需装 Flutter，两台电脑已具备；React 嵌入线仅依赖 Node）；WinSW 多服务命名/端口管理复杂度；认证 token 的存储安全仍属 D10 边界内的最小实现
