## 1. 控制台工程脚手架

- [ ] 1.1 `console/` Flutter 工程初始化（flutter create，web+android 平台），建立 lib/ 骨架（config/core/features/services/viewmodels/widgets）与依赖（provider/go_router/http），配置 .gitignore（build/ 等）；验证 `flutter build web` 可出产物
- [ ] 1.2 统一 HTTP 客户端（BASE_URL：Web 同源/APP 可配置、令牌头注入、错误码→可读消息映射、401 跳登录回调）；验证对运行中后端发起请求成功与错误两路呈现

## 2. 最小认证（后端 + 登录页）

- [ ] 2.1 后端认证（design D3）：`deploy.auth.token` 配置、登录端点（比对+内存会话 12h TTL）、HandlerInterceptor 白名单（login/静态/h2-console）、空令牌=零鉴权兼容模式；验证：启用时无令牌 401/白名单放行/登录后放行、关闭时 curl 旧行为不变（单测+curl）
- [ ] 2.2 登录页与路由守卫（go_router redirect）；验证：未登录访问任一页跳登录、登录成功进仪表盘、令牌失效回登录
- [ ] 2.3 后端 dev CORS（仅 dev profile 放行 8090）与 `console/scripts/start-dev.bat`（一键起后端+flutter run chrome）；验证脚本一次拉起两端互通

## 3. 核心页面域

- [ ] 3.1 仪表盘与项目域（状态汇总卡、项目列表/详情/注册表单）；验证：真实数据渲染、注册项目后列表刷新
- [ ] 3.2 发布单域（详情页状态机事件时间线、触发构建按钮、构建日志滚动窗：1s 轮询 offset 续读、终态停止）；验证：触发构建后日志实时追加、BUILT/FAILED 呈现
- [ ] 3.3 制品库与目标环境域（列表、平台标签徽章、sha256/下载、五维卡片、健康状态灯）；验证：PORTABLE 与绑定制品标签正确、下载可用
- [ ] 3.4 部署域（触发部署、进度轮询、回滚按钮、部署历史时间线）；验证：对 E2E 环境完成一次部署→回滚全操作链

## 4. FLUTTER 构建类型与单体 jar

- [ ] 4.1 `BuildType.FLUTTER`（design D5）：命令序列 pub get→build web→(配置)build apk、flutter 缺失明确报错、产物发现规则（build/web、flutter-apk/*.apk）；验证单测 + 对 console 工程真实构建成功
- [ ] 4.2 Web 产物嵌入单体 jar（design D2）：static/ gitignore、构建流程拷贝产物→bootJar→jar 即全栈制品；验证：单体 jar 本地启动浏览器根路径呈现控制台
- [ ] 4.3 APK 平台绑定入库（android/arm64）+ android 目标注册路由匹配；验证：含 APK 发布单对 android 目标路由命中

## 5. 实例扩缩容

- [ ] 5.1 实例模型与表（design D4）：instance 表、env.basePort 配置、端口算术、seq=1 向后兼容既有单实例；验证单测端口分配与模型约束
- [ ] 5.2 ServiceManager 实例化扩展：`instanceServiceId`/定义与命令带实例参数（systemd 模板单元 app@N、WinSW app-N 服务名+端口注入）；验证两适配器单测生成内容正确
- [ ] 5.3 扩缩容编排与 API（对新增 seq 逐个 deliver、缩容裁尾 deactivate、实例级健康检查、按环境查询实例矩阵）；验证：单测编排逻辑 + 本机真实环境扩容 1→3→缩容 1 全链路（实例端口独立可访问）
- [ ] 5.4 扩容前端页（实例数调整控件、实例健康矩阵实时状态）；验证：页面发起扩容看到矩阵逐个变健康

## 6. 端到端验收与收尾

- [ ] 6.1 全链路自测：console 改一行 UI 文案→平台触发 FLUTTER 构建→单体 jar 入库→交付本机→浏览器访问验证新文案→APK 产物入库可下载（自举闭环）；留存每步输出证据
- [ ] 6.2 异常演练：认证关闭/开启两模式 curl 兼容、flutter 缺失构建失败消息、扩容中新实例不健康失败不影响存量实例；验证错误信息准确
- [ ] 6.3 收尾：核对 tasks.md 全勾、输出改造点总结列表（协作规则）、已知问题记录（令牌明文/缩容对账缺失/iOS 与鸿蒙未开启）
