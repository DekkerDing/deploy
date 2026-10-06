## Purpose

让平台把 Flutter 工程当作一等构建对象：新增 FLUTTER 构建类型（flutter build web / apk 子进程），Web 产物嵌入后端 jar 形成"前后端一体"的全栈单体交付物，APK 作为平台绑定制品入库——平台由此可以用自己发布自己（自举）。

## ADDED Requirements

### Requirement: FLUTTER 构建类型
项目构建类型 SHALL 支持 FLUTTER：构建时以子进程执行 flutter 构建命令序列（Web 与 APK 按项目配置产出），命令缺失（flutter 不在 PATH）时构建立即失败并指明缺失命令；构建超时与失败处理与既有构建类型一致（FAILED + 退出码 + 日志尾摘要）。

#### Scenario: 构建成功产出双形态制品
- **WHEN** 对 FLUTTER 项目触发构建
- **THEN** 构建完成后制品库登记 Web 产物与 APK（若配置产出），各含大小与 sha256

#### Scenario: flutter 命令缺失
- **WHEN** 构建机上 flutter 命令不可用
- **THEN** 发布单转 FAILED，错误消息指明 flutter 未安装

### Requirement: Web 产物嵌入单体 jar
FLUTTER 项目（或配置了前端嵌入的项目）构建时，Web 产物 SHALL 被嵌入后端 jar 的静态资源目录，产出单一全栈制品（页面 + API 同进程同源）；该制品沿用现有 JVM 可移植制品的入库与交付链路。

#### Scenario: 单体制品可交付
- **WHEN** 全栈单体 jar 构建完成并交付到目标环境
- **THEN** 服务启动后浏览器访问根路径呈现控制台页面，API 同源可用

#### Scenario: 版本升级页面同步更新
- **WHEN** 同项目新版本单体 jar 交付替换旧版本
- **THEN** 访问根路径呈现新版本页面（无旧资源残留）

### Requirement: APK 平台绑定入库
APK 产物 SHALL 以平台绑定制品登记（os=android，arch 按产物声明或默认 arm64），可被目标环境模型中 android 目标精确匹配路由；无匹配目标时不影响其他制品的正常交付。

#### Scenario: APK 路由匹配
- **WHEN** 存在 android/arm64 目标环境且发布单含 APK 制品
- **THEN** 路由可精确选中 APK 交付
