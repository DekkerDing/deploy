## 1. 前端工程脚手架（用户已自建，收编）

- [x] 1.1 双端工程初始化：`frontend-web/`（React 18 + TS + Vite 5 + Tailwind + zustand + axios + react-router-dom，vite proxy `/api`→8080，生产同源）与 `frontend/`（Flutter，MVVM + Provider + go_router + features/{域}/，参照 xhbx-pl-flutter）；验证：代码级核对（页面域/HTTP 封装/路由完整），真机全链路并入 6.1 复核
- [x] 1.2 统一 HTTP 客户端与单体 jar 嵌入：双端 ApiException 封装（api.ts 拦截器 / http_client.dart 错误码映射）、React dist 经 Gradle 任务链嵌入（reactInstall→reactBuild→cleanStatic→copyReactAssets→processResources）+ WebMvcConfig SPA fallback；验证：任务链配置核对通过，单体 jar 根路径呈现控制台并入 6.1 复核

## 2. 最小认证（后端 + 登录页）

- [ ] 2.1 后端认证（design D3）：`deploy.auth.token` 配置、登录端点（比对+内存会话 12h TTL）、HandlerInterceptor 白名单（login/静态/h2-console）、空令牌=零鉴权兼容模式；验证：启用时无令牌 401/白名单放行/登录后放行、关闭时 curl 旧行为不变（单测+curl）
- [ ] 2.2 登录页与路由守卫（Web：react-router 守卫；APP：go_router redirect）；验证：未登录访问任一页跳登录、登录成功进仪表盘、令牌失效回登录
- [ ] 2.3 认证异常演练：关闭/开启两模式 curl 兼容、令牌失效前端跳转呈现；验证错误信息准确

## 3. 核心页面域（页面骨架已有，补关键能力）

- [x] 3.1 仪表盘与项目域（双端：状态汇总、项目列表/详情/注册表单）；验证：代码级核对通过，真机注册→列表刷新链路并入 6.1 复核
- [ ] 3.2 构建日志滚动窗（双端）：发布单详情页挂日志面板——1s 轮询 `/build-log?offset=` 续读（nextOffset）、release 终态停止、自动滚动+用户上滚暂停跟随、超长日志仅保留尾部防 DOM 膨胀；验证：触发构建后日志实时追加、BUILT/FAILED 呈现且轮询停止（真机）
- [ ] 3.3 制品库与目标环境域补全：制品下载可用（storage 路由核对）、sha256/平台标签展示、健康状态灯；验证：PORTABLE 与绑定制品标签正确、下载可用（真机）
- [ ] 3.4 部署域修复与补全：修复 targetEnvId 传参契约（双端现为 JSON body，后端 @RequestParam 必 400）、环境选择（现硬编码 0）、部署进度轮询、部署历史时间线完善；验证：对 E2E 环境完成一次部署→回滚全操作链（真机）

## 4. FLUTTER 构建类型与 APK 制品

- [ ] 4.1 `BuildType.FLUTTER`（design D5）：命令序列 pub get→build apk、flutter 缺失明确报错、产物发现规则（flutter-apk/*.apk→android 绑定制品）；验证单测 + 对 `frontend/` 工程真实构建成功
- [x] 4.2 前端产物嵌入单体 jar（design D2）：React dist 经 Gradle 任务链嵌入 build/resources/main/static、SPA fallback；验证：任务链核对通过，单体 jar 启动呈现控制台并入 6.1 复核（用户已实现）
- [ ] 4.3 APK 平台绑定入库（android/arm64）+ android 目标注册路由匹配；验证：含 APK 发布单对 android 目标路由命中

## 5. 实例扩缩容

- [ ] 5.1 实例模型与表（design D4）：instance 表、env.basePort 配置、端口算术、seq=1 向后兼容既有单实例；验证单测端口分配与模型约束
- [ ] 5.2 ServiceManager 实例化扩展：`instanceServiceId`/定义与命令带实例参数（systemd 模板单元 app@N、WinSW app-N 服务名+端口注入）；验证两适配器单测生成内容正确
- [ ] 5.3 扩缩容编排与 API（对新增 seq 逐个 deliver、缩容裁尾 deactivate、实例级健康检查、按环境查询实例矩阵）；验证：单测编排逻辑 + 本机真实环境扩容 1→3→缩容 1 全链路（实例端口独立可访问）
- [ ] 5.4 扩容前端页（实例数调整控件、实例健康矩阵实时状态，Web 优先）；验证：页面发起扩容看到矩阵逐个变健康（真机）

## 6. 端到端验收与收尾

- [ ] 6.1 全链路自测（含 1.1/1.2/3.1/4.2 遗留真机复核）：Web 控制台改一行 UI 文案→平台触发构建→单体 jar 入库→交付本机→浏览器访问验证新文案→APK 产物入库可下载（自举闭环）；留存每步输出证据
- [ ] 6.2 异常演练：flutter 缺失构建失败消息、扩容中新实例不健康失败不影响存量实例（认证演练已前移 2.3）；验证错误信息准确
- [ ] 6.3 收尾：核对 tasks.md 全勾、输出改造点总结列表（协作规则）、已知问题记录（令牌明文/缩容对账缺失/iOS 与鸿蒙未开启）
