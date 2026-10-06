## 1. 仓库整顿（动工前置）

- [x] 1.1 删除 `serverless/.git` 嵌套仓库并将代码并入外层仓库提交；验证 `git status` 不再出现嵌套库、历史可追溯
- [x] 1.2 恢复根 `.gitignore` 并补充 `build/ target/ .idea/ logs/ storage/ *.h2.db` 等条目；验证 `git status` 不显示构建产物与 IDE 文件
- [x] 1.3 删除 `demos/web` 演示代码，包重命名为 `io.github.dekkerding.deploy`，`settings.gradle` 的 `rootProject.name` 改为 `deploy-platform`；验证 `gradlew build` 编译通过
- [x] 1.4 `bootstrap.yaml` 重命名为 `application.yaml`，清理死注释并建立基础配置段（端口、存储目录、H2）；验证应用启动无报错

## 2. 领域模型与存储

- [x] 2.1 `build.gradle` 引入 H2、MyBatis-Plus、sshj 依赖；验证 `gradlew build` 依赖解析与编译通过
- [x] 2.2 建立六张表（project / release / release_event / artifact / target_env / deployment）的实体与 Mapper；验证应用启动自动建表、H2 console 可查
- [x] 2.3 实现 `ReleaseState` 状态机（枚举+显式转移表，非法转移抛领域异常，每次转移追加 release_event）；验证单测覆盖全部合法流转与典型非法流转
- [x] 2.4 实现项目与发布单 REST API（注册/列表/详情/创建发布单/触发/状态查询）；验证 curl 走通 CRUD、重复项目名与非法状态流转返回明确错误

## 3. 构建执行

- [x] 3.1 定义 `BuildExecutor` SPI 与 `BuildContext`/`BuildResult` 模型；验证编译通过、接口可被多实现注册
- [x] 3.2 实现 `ProcessBuildExecutor`（按构建类型组装 mvn/gradle/npm 命令，命令缺失时立即失败并指明缺失命令）；验证对一个样例 Java 项目真实构建出 jar
- [x] 3.3 构建日志流式落盘 `logs/{releaseId}/build.log` 并提供偏移量增量读取 API；验证构建中轮询能看到日志增长、平台重启后仍可读历史
- [x] 3.4 固定线程池+有界队列的并发控制；验证两个构建并发互不串扰、超上限任务排队不失败
- [x] 3.5 构建超时与失败处理（destroyForcibly、FAILED 状态+退出码与日志尾摘要）；验证人为制造编译错误时发布单正确转 FAILED

## 4. 制品管理

- [x] 4.1 构建产物落盘 `storage/{project}/{version}/` 并登记元数据（文件名/大小/sha256/releaseId）；验证 API 可查询到入库制品
- [x] 4.2 制品平台描述符与可移植性建模（PORTABLE 或 os+arch+libc 声明）；验证纯 JAR 自动标记 PORTABLE、绑定制品正确声明平台
- [x] 4.3 制品列表与下载 API；验证下载内容与入库 sha256 一致
- [x] 4.4 同版本覆盖保护；验证重复版本写入被拒绝且原制品不变

## 5. 交付路由

- [x] 5.1 `TargetEnv` 维度模型（os/arch/libc/载体/通道）与注册/列表 API；验证注册一个未列举系统（如 freebsd/amd64）无需改代码即可成功
- [x] 5.2 探针实现（类 Unix `uname -sm`、Windows 处理器架构变量→平台描述符；无法识别标记 UNKNOWN 并阻止交付）；验证解析单测矩阵 + 对本机真实探针结果正确
- [x] 5.3 实现 `RoutingResolver`（精确匹配→PORTABLE 回退→拒绝并列出制品平台清单）；验证单测覆盖命中/回退/拒绝三类用例
- [x] 5.4 JAR 字节码版本与目标 JVM 预检（major version 对比）；验证单测：JDK8 字节码→JVM8 通过、高版本字节码→JVM8 部署前拒绝

## 6. SSH JAR 交付 Provider

- [x] 6.1 定义 `DeliveryProvider` SPI 与部署记录（deployment）生成；验证编译通过、SPI 可注册多实现
- [x] 6.2 基于 sshj 的通道能力（连接认证/SFTP 上传/远程命令执行，命令显式指定 shell）；验证对本机 OpenSSH 完成一次文件上传与命令执行
- [x] 6.3 `PathPolicy` 跨 OS 路径策略（linux `/opt/<项目>/<版本>/`、windows `C:\apps\<项目>\<版本>\`，旧版本保留）；验证路径生成单测双平台正确
- [ ] 6.4 `SystemdAdapter`（unit 模板生成、daemon-reload、restart）；验证模板生成单测正确（真实 systemd 待 linux 目标接入后验证）
- [ ] 6.5 `WinSwAdapter`（WinSW XML 配置生成、Windows 服务安装与重启，指向新版本制品）；验证本机生成 XML 并成功安装/启动一个真实服务
- [ ] 6.6 部署后健康检查（TCP 端口探活+重试窗口）；验证服务在线判定成功、不可达判定超时失败两路自测
- [ ] 6.7 回滚（切换到该目标上一次成功版本并重启+健康检查）；验证连续两次部署后回滚到首版本成功
- [ ] 6.8 部署历史查询 API（按发布单/按目标环境）；验证 curl 返回按时间排序的部署记录

## 7. 端到端闭环验收

- [ ] 7.1 全链路串联自测（电脑 A 自举）：注册项目→触发构建→制品入库→路由解析→SSH 交付到本机目标→健康检查→DEPLOYED；验证一条 curl/脚本序列跑通并留存每步输出证据
- [ ] 7.2 异常路径演练：无匹配制品交付被拒（附平台清单）、非 BUILT 状态交付被拒、构建失败转 FAILED；验证三类错误信息准确
- [ ] 7.3 收尾：核对 tasks.md 全部勾选、输出改造点总结列表（对应协作规则）；验证总结与实际 diff 一致
