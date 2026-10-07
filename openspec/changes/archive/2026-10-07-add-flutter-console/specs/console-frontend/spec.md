## Purpose

为 deploy-platform 提供可视操作面：两个前端工程与后端同仓（monorepo）——`frontend-web/`（React）产出 Web 控制台（随单体 jar 同源部署），`frontend/`（Flutter）产出移动 APP（Android）。两端共享同一 API 契约与页面域划分，覆盖项目/发布单/制品/目标环境/部署/回滚/扩容全流程的查询与操作。

## ADDED Requirements

### Requirement: 双端工程
控制台 SHALL 由两个工程分别产出 Web 与移动端：Web 控制台（React）随单体 jar 同源部署（浏览器访问服务根路径即得），Android APK（Flutter）为独立安装的移动运维端。两端 SHALL 共享同一套 API 契约与页面域划分。

#### Scenario: Web 产物随服务发布
- **WHEN** 后端服务启动且嵌入了控制台 Web 产物
- **THEN** 浏览器访问服务根路径即呈现控制台登录页，无需单独部署前端服务

#### Scenario: APK 独立安装
- **WHEN** 用户在 Android 设备安装控制台 APK 并配置平台地址
- **THEN** APP 可登录并执行与 Web 端一致的核心查询与操作

### Requirement: 页面域覆盖
控制台 SHALL 提供以下页面域：登录、仪表盘（最近构建/部署状态汇总）、项目管理（列表/详情/注册）、发布单（状态机事件时间线、构建日志实时滚动、触发构建）、制品库（列表、平台标签、sha256、下载）、目标环境（五维信息、健康状态）、部署（触发、进度、回滚、历史时间线）、实例扩容（实例数调整与实例健康矩阵）。

#### Scenario: 构建日志实时滚动
- **WHEN** 发布单处于构建中且用户停留在日志页
- **THEN** 页面按增量接口的偏移量语义持续追加日志内容，构建结束后停止轮询

#### Scenario: 状态机时间线
- **WHEN** 用户打开发布单详情
- **THEN** 按时间顺序展示该发布单的全部状态事件（从/至状态与说明）

#### Scenario: 回滚操作
- **WHEN** 用户对 DEPLOYED 状态发布单在目标环境卡片点击回滚
- **THEN** 发起回滚请求并展示进度直至回滚完成或失败

### Requirement: API 客户端与登录态
两端 SHALL 各自通过统一 HTTP 客户端访问平台 API：附加认证令牌头、统一错误码呈现（业务拒绝与服务器错误可区分）、平台地址可配置（APP 端经 AppConfig 修改，Web 端开发期走 vite proxy、生产固定同源）。

#### Scenario: 业务错误呈现
- **WHEN** API 返回业务拒绝响应
- **THEN** 页面展示可读错误消息而非静默失败或崩溃

#### Scenario: 令牌失效
- **WHEN** 认证令牌缺失或无效
- **THEN** 跳转登录页，重新登录后返回原操作上下文
