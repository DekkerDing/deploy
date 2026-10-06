## 1. 前置核对与扩展 API 面

- [x] 1.1 核对前置 change `mvp-build-delivery-platform` 已实施且验收通过（BuildExecutor/DeliveryProvider 注册表存在、交付模式可经 API 枚举）；验证：MVP tasks.md 全勾 + 注册表代码可见，不满足则暂停并提示先实施前置
- [x] 1.2 定义 `ExtensionApiVersion` 常量与兼容判定（主版本相等且插件次版本 ≤ 平台）；验证：判定规则单测覆盖 相等/次版本低/主版本不符 三类
- [x] 1.3 既有注册表补充编程式注册入口（供插件实例注册，非 Spring Bean 路径）；验证：单测以编程方式注册一个内建实现并经 API 枚举到

## 2. 插件加载核心

- [x] 2.1 新增插件目录配置项（`plugins.dir`，默认 `./plugins`）与禁用子目录约定；验证：配置读取单测 + 指南文档写明约定
- [x] 2.2 启动期扫描插件目录、构造 `URLClassLoader`、对受支持扩展点执行 ServiceLoader 加载并注册；验证：空目录与目录不存在两种情况平台均正常启动、无错误日志
- [x] 2.3 manifest 解析（Plugin-Id / Plugin-Version / Extension-Api-Version），缺属性判为无效插件；验证：三种缺属性各一例的单测均得到对应失败原因

## 3. 兼容与失败隔离

- [x] 3.1 版本兼容校验接入加载流程，不兼容插件被拒并输出"插件 id + 声明版本 + 平台版本"；验证：构造 1.x 插件对 2.x 平台的单测拒绝用例与 1.0 对 1.1 的放行用例
- [x] 3.2 坏插件隔离：损坏 jar、类缺失（ServiceConfigurationError）、实例化异常三类失败均不阻止启动且不影响同目录其他插件；验证：三类坏插件 + 一个好插件同目录的加载单测，好插件成功、三条失败记录齐全

## 4. 清单与禁用

- [ ] 4.1 插件清单查询 API（已加载：id/版本/贡献扩展点计数；失败：标识或文件名+原因）；验证：混合加载后 curl 返回与磁盘内容一致的清单
- [ ] 4.2 禁用机制（移入禁用子目录后下次启动不加载）；验证：成功插件移入禁用目录重启后从清单消失、其扩展点从 API 消失

## 5. 示例插件与端到端

- [ ] 5.1 新增 `example-plugin` Gradle 子模块：最小 `script` 类型 BuildExecutor，shade 打包 + manifest 属性注入 + services 声明；验证：`gradlew :example-plugin:build` 产出含正确 manifest 与 services 的 jar
- [ ] 5.2 端到端验证：示例 jar 放入 plugins/ → 重启 → `script` 构建类型出现在 API → 用它对一个样例项目完成一次真实构建；验证：留存每步输出证据（放入前后 API 对比、构建成功日志）
- [ ] 5.3 端到端反向验证：示例 jar 移入禁用目录 → 重启 → `script` 类型消失且原有能力不受影响；验证：API 前后对比记录
- [ ] 5.4 编写插件开发指南（打包约定/shade 要求/manifest 属性/信任边界/禁用方式）；验证：按指南从零打出第二个可加载插件（可复用 5.1 源码改 id 验证）

## 6. 收尾

- [ ] 6.1 核对 tasks.md 全部勾选并输出改造点总结列表（对应协作规则）；验证：总结与实际 diff 一致、`openspec validate plugin-system` 通过
