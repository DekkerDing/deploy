## 1. 前端工程脚手架（用户已自建，收编）

- [x] 1.1 双端工程初始化：`frontend-web/`（React 18 + TS + Vite 5 + Tailwind + zustand + axios + react-router-dom，vite proxy `/api`→8080，生产同源）与 `frontend/`（Flutter，MVVM + Provider + go_router + features/{域}/，参照 xhbx-pl-flutter）；验证：代码级核对（页面域/HTTP 封装/路由完整），真机全链路并入 6.1 复核
- [x] 1.2 统一 HTTP 客户端与单体 jar 嵌入：双端 ApiException 封装（api.ts 拦截器 / http_client.dart 错误码映射）、React dist 经 Gradle 任务链嵌入（reactInstall→reactBuild→cleanStatic→copyReactAssets→processResources）+ WebMvcConfig SPA fallback；验证：任务链配置核对通过，单体 jar 根路径呈现控制台并入 6.1 复核

## 2. 最小认证（后端 + 登录页）

- [x] 2.1 后端认证（design D3）：`deploy.auth.token` 配置、登录端点（比对+内存会话 12h TTL）、HandlerInterceptor 白名单（login/静态/h2-console）、空令牌=零鉴权兼容模式；验证：单测 10/10（启用 401/白名单/登录放行/?session= 直链/伪造拒绝/登出失效/TTL 过期、兼容模式放行+login 明确报未启用）+ 全量回归 100/100 + 真机 curl 启用模式 10 项全符合（无会话 401/错令牌"令牌不匹配"不回显/登录 sessionId+43200s/头与 query 会话放行/h2-console 302/静态 200/制品下载带 session 200）
- [x] 2.2 登录页与路由守卫（Web：react-router 守卫；APP：go_router redirect）；验证：Web tsc+build 过（RequireAuth 守卫探测兼容模式无感放行、401 拦截器跳 /login、Layout 退出按钮、下载直链带 ?session=）；APP analyze 0 error+冒烟测试过（AuthSession 单例 ChangeNotifier + refreshListenable redirect、登录成功自动回首页、401 触发跳登录）；浏览器/真机人验并入 6.1
- [x] 2.3 认证异常演练：关闭/开启两模式 curl 兼容（关闭=旧行为 200 + login 400"未启用认证"；开启=10 项序列见 2.1）、令牌失效前端跳转（401 响应体"未认证或会话已过期"→Web 拦截器清会话跳 /login /APP onUnauthorized→redirect /login）；验证错误信息准确

## 3. 核心页面域（页面骨架已有，补关键能力）

- [x] 3.1 仪表盘与项目域（双端：状态汇总、项目列表/详情/注册表单）；验证：代码级核对通过，真机注册→列表刷新链路并入 6.1 复核
- [x] 3.2 构建日志滚动窗（双端）：发布单详情页挂日志面板——1s 轮询 `/build-log?offset=` 续读（nextOffset）、release 终态停止、自动滚动+用户上滚暂停跟随、超长日志仅保留尾部防 DOM 膨胀；验证：触发构建后日志实时追加、BUILT/FAILED 呈现且轮询停止（真机 API 序列：offset 0→91→357 无重复、无新增时 nextOffset 停留、终态 chunk 携带收尾输出；Web tsc+build 过、Flutter analyze 零 error+冒烟测试过；浏览器/APP 视觉呈现并入 6.1）
- [x] 3.3 制品库与目标环境域补全：制品下载可用（后端 /artifacts/{id}/download 已有，双端接线：Web `<a>` 下载 / APP url_launcher 外部浏览器）、sha256/平台标签展示、健康状态灯（每环境最近部署 SUCCESS/FAILED/未部署 着色）；验证：真机下载 artifact#161 sha256 逐字节一致 + Content-Disposition/X-Artifact-Sha256 头正确、PORTABLE 标签（portable=true 无平台绑定）正确
- [x] 3.4 部署域修复与补全：修复 targetEnvId 传参契约（双端 JSON body→query string，原必 400）、Web 环境选择对话框（原硬编码 0）、双端部署进度轮询（终态自停+刷新历史）、部署历史时间线（耗时/message/ROLLED_BACK 着色）；顺带修复三处后端/契约缺陷——detail 事件字段名对齐（后端 timeline，双端原读 events 致 Web 崩/APP 空）、回滚失败 catch 块 DEPLOYED→FAILED 非法转换掩盖真实原因（改 noteFailure 保持 DEPLOYED 可重试）、installAndActivate 先传后停在 Windows 文件锁下必败（改为先幂等停用再上传）；验证：真机 release#321 部署（dep#97 SUCCESS 9s）→ 回滚（dep#161 SUCCESS"回滚到 1.0.1+健康检查通过"，hello-server-jar Running），后端 90/90 测试含新增"回滚失败保持 DEPLOYED 留痕并可重试"用例

## 4. FLUTTER 构建类型与 APK 制品

- [x] 4.1 `BuildType.FLUTTER`（design D5）：命令序列 pub get→build apk、flutter 缺失明确报错、产物发现规则（flutter-apk/*.apk→android 绑定制品）；验证单测 + 对 `frontend/` 工程真实构建成功（真机：平台注册 deploy-console-app→触发构建 BUILT，APK 51,892,558B 入库绑定 android/arm64；直跑 flutter build apk 49.5MB 亦成功——gradle 腾讯镜像+kotlin.incremental=false 排障链路留档）
- [x] 4.2 前端产物嵌入单体 jar（design D2）：React dist 经 Gradle 任务链嵌入 build/resources/main/static、SPA fallback；验证：任务链核对通过，单体 jar 启动呈现控制台并入 6.1 复核（用户已实现）
- [x] 4.3 APK 平台绑定入库（android/arm64）+ android 目标注册路由匹配；验证：含 APK 发布单对 android 目标路由命中（ArtifactPlatformDetectorTest `.apk→android/arm64/libc=null`、RoutingResolverTest android 精确匹配命中 + 非 android 目标拒绝，构建域 23/23）

## 5. 实例扩缩容

- [x] 5.1 实例模型与表（design D4）：instance 表、env.basePort 配置、端口算术、seq=1 向后兼容既有单实例；验证单测端口分配与模型约束（InstanceServiceTest 4/4：basePort+seq-1 错开、无 basePort 时 seq=1 沿用 healthCheckPort、seq>1 无 basePort 拒绝、record 幂等）
- [x] 5.2 ServiceManager 实例化扩展：`instanceServiceId`/定义与命令带实例参数（systemd 模板单元 app@N、WinSW app-N 服务名+端口注入）；验证两适配器单测生成内容正确（SystemdAdapterTest 4/4 + WinSwAdapterTest 4/4：多实例单元名带实例号、注入 --server.port、start 不 restart 存量、stop 命令正确）
- [x] 5.3 扩缩容编排与 API（对新增 seq 逐个 deliver、缩容裁尾 deactivate、实例级健康检查、按环境查询实例矩阵）；验证：单测编排逻辑 + 本机真实环境扩容 1→3→缩容 1 全链路（实例端口独立可访问）（真机 local-win-scale-b：1→3 矩阵逐个变健康、TCP echo 三端口各回显自身端口号、三 WinSW 服务 RUNNING；缩容 3→1 裁尾、-2/-3 服务注销端口释放、seq=1 全程健康；排障留档：扩容重传共享目录 jar 被 RUNNING 实例锁定 → SFTP Failure，修复=扩缩容 ctx skipArtifactUpload 仅传实例独立服务定义）
- [x] 5.4 扩容前端页（实例数调整控件、实例健康矩阵实时状态，Web 优先）；验证：页面发起扩容看到矩阵逐个变健康（真机）（Web 实例矩阵对话框 2s 轮询 /instances、目标实例数 number 输入 + 调整按钮 + message 反馈、注册表单 basePort；tsc+build 过、真机 API 序列矩阵 1→2→3 行实时演进即页面数据源；浏览器视觉呈现并入 6.1）

## 6. 端到端验收与收尾

- [ ] 6.1 全链路自测（含 1.1/1.2/3.1/4.2 遗留真机复核）：Web 控制台改一行 UI 文案→平台触发构建→单体 jar 入库→交付本机→浏览器访问验证新文案→APK 产物入库可下载（自举闭环）；留存每步输出证据
- [ ] 6.2 异常演练：flutter 缺失构建失败消息、扩容中新实例不健康失败不影响存量实例（认证演练已前移 2.3）；验证错误信息准确
- [ ] 6.3 收尾：核对 tasks.md 全勾、输出改造点总结列表（协作规则）、已知问题记录（令牌明文/缩容对账缺失/iOS 与鸿蒙未开启/同环境多服务共享 healthCheckPort 时探活假阳性——instance-scaling 独立端口模型将系统化解决）
