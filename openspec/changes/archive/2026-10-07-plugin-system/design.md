## Context

前置 change `mvp-build-delivery-platform` 建立了三个编译期 SPI（BuildExecutor / DeliveryProvider + ServiceManagerAdapter）与对应注册表（其 design.md D5/D7）。本 change 在其之上引入运行时加载。基座约束不变：JDK8 + Spring Boot 2.6.14。动机见 proposal.md。

## Goals / Non-Goals

**Goals:**
- "放 jar → 重启 → 新能力出现在 API"的最小闭环，零新增第三方依赖（纯 JDK 设施）
- 坏插件零爆炸半径：启动永不因插件失败而失败
- 插件状态可观测：清单 API + 逐插件失败原因
- 插件开发体验有参照物（示例插件子模块 + 开发指南）

**Non-Goals（设计层面排除）:**
- 不做热加载/热卸载（加载仅发生在启动期）
- 不做 per-plugin 类加载隔离与插件间依赖仲裁（单一共享插件类加载器）
- 不做插件间依赖声明、签名校验、更新中心

## Decisions

### D1. 加载机制：URLClassLoader + ServiceLoader（纯 JDK），PF4J 延后
- 插件目录下的 jar 构造一个 `URLClassLoader`（parent = 平台类加载器），对每个受支持扩展点执行 `ServiceLoader.load(ext, pluginLoader)`，实例化后编程式注册进既有注册表
- 备选：直接引入 PF4J（类隔离/生命周期/热插拔一步到位）——被否：个人规模下复杂度收益倒挂，且 ServiceLoader 阶段验证的是同一套扩展点契约，后续升级 PF4J 时插件包约定可平滑迁移（PF4J 也消费 services 声明）
- 演进路径承诺：SPI（已完成）→ ServiceLoader（本 change）→ PF4J（未来 change），与探索期对 Jenkins 插件演进的结论一致

### D2. 类加载策略：单一共享插件类加载器，插件自带依赖须 shade
- 所有插件 jar 共享一个 append 式类加载器（parent-first），插件对平台的依赖以 "provided" 方式引用（不重复打包平台类）
- 插件自身引入的第三方库必须 shade 进插件 jar——写入开发指南并作为示例插件的示范
- 风险（插件间同名类冲突）显式接受：冲突时行为不可预测，规避责任在插件作者；per-plugin 隔离留给 PF4J 阶段

### D3. 插件元数据：jar manifest 属性，不引入独立描述符
- `Plugin-Id` / `Plugin-Version` / `Extension-Api-Version` 三属性 + `META-INF/services` 声明即完整约定——全部是 JDK 生态既有惯例，零新文件格式
- 备选：plugin.properties / YAML 描述符——被否：多一个文件多一类解析失败，且 manifest 在构建期即可由 Gradle 自动注入

### D4. 兼容判定规则：主版本相等且插件次版本 ≤ 平台
- 平台侧维护 `ExtensionApiVersion` 常量（如 `1.0`），随扩展点接口的破坏性变更手动提升主版本
- 判定：`plugin.major == platform.major && plugin.minor <= platform.minor`；不满足即拒绝并输出双方版本（对应 spec 的错误信息要求）
- 语义：主版本差异 = 接口不兼容；平台次版本更高 = 向后兼容的新能力，旧插件可用

### D5. 注册对接：编程式注册进既有注册表
- MVP 注册表若为 Spring 组件扫描驱动，则补充编程式注册入口（`register(executor/provider)`），插件实例不作为 Spring Bean 管理（其依赖注入由平台在注册时显式传入所需上下文）
- 插件实例的生命周期归平台启动器统一持有（启动加载、关闭时置空），无热插拔状态机

### D6. 失败隔离实现：逐 jar / 逐声明 try-catch，永不向上抛
- 加载循环的三层捕获点：jar 读取（损坏/非 jar）→ manifest 解析（缺属性）→ ServiceLoader 迭代（`ServiceConfigurationError`：类缺失/实例化异常），各自落为一条插件失败记录
- 平台启动流程对插件阶段的任何异常兜底为"记录并继续"，对应 spec 的零爆炸半径要求

### D7. 示例插件：独立 Gradle 子模块 `example-plugin`
- 实现一个最小 `BuildExecutor`（如 `script` 类型：执行一段声明式脚本命令），shade 打包 + manifest 注入 + services 声明，端到端验证用它完成一次真实构建
- 选 BuildExecutor 而非 DeliveryProvider 作为示例：验证闭环不需要真实目标机，本机即可完成"放 jar → 模式出现 → 用它构建成功"全链路

### D8. 前置依赖与实施顺序
- 本 change 的 apply 必须晚于 `mvp-build-delivery-platform` 的 apply（注册表与扩展点先存在）；tasks 第一组即核对前置完成度，不满足则暂停并提示

## Risks / Trade-offs

- [共享类加载器下插件依赖冲突行为不可预测] → D2 shade 约定 + 开发指南明示；彻底解法（per-plugin 隔离）归 PF4J change
- [任意 jar 即任意代码执行（与 Jenkins 同级信任模型）] → 插件目录即信任边界，写入指南；API 无鉴权阶段该目录只应由管理员访问（承接 MVP D10 的安全边界）
- [ServiceLoader 迭代中部分成功部分失败] → 逐声明捕获，同一 jar 内成功者照常注册，失败者记录（粒度：services 声明条目）
- [parent-first 导致插件误用旧版平台类] → 平台接口无版本并存（单进程单版本），该风险仅在 shade 不彻底时与 D2 冲突条款合并处理

## Migration Plan

绿地增量能力，无存量迁移。回滚 = 移除 plugins/ 目录下 jar 并重启；代码回滚 = git revert。对无插件部署，行为与 MVP 完全一致（空目录快速通过）。

## Open Questions

（无——热插拔与 PF4J 已明确划归后续 change，不影响本次结构；插件目录默认路径取 `./plugins` 还是随 `storage` 同级的配置化路径，属实现期小节，tasks 中按配置项落地）
