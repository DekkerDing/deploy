# 插件化系统：运行时可插拔扩展（第一阶段）

## Why

MVP 平台的三个扩展点（BuildExecutor / DeliveryProvider / 跨 OS 适配器）是编译期 SPI——新增一种交付模式或构建类型必须修改平台源码并重新编译。"类 Jenkins 可插拔"的实质是：**不改平台源码，放置一个 jar 即完成扩展**。本 change 将扩展点升级为重启期运行时加载，让交付模式生态（Docker/K8s/Serverless Provider、python/go 执行器）可以以插件形式独立演进。

## What Changes

- **插件打包约定**：插件 = 一个 jar，含 `META-INF/services` 扩展点注册文件 + manifest 元数据（`Plugin-Id` / `Plugin-Version` / `Extension-Api-Version`）
- **启动发现与加载**：平台启动时扫描配置的 `plugins/` 目录，经 `URLClassLoader` + `ServiceLoader` 加载插件实现，注册进既有 SPI 注册表（加载后插件提供的交付模式/构建类型经既有 API 自然可见）
- **版本兼容检查**：插件声明所基于的扩展 API 版本；与平台不兼容的插件被拒绝加载，错误指明插件、要求版本与实际版本
- **失败隔离**：损坏的 jar、缺失类、实例化异常等坏插件不得阻止平台启动或影响其他插件；失败逐插件记录
- **插件清单 API**：查询已加载插件（id/版本/贡献的扩展点）与失败插件（原因）
- **禁用机制**：将 jar 移入禁用目录（或改名）即可在下次启动排除
- **示例插件**：新增独立 Gradle 子模块作为机制验证与插件开发参照
- **明确不做（本次范围外）**：热加载/热卸载（不重启生效）、per-plugin 类加载隔离、插件管理 UI 与更新中心——归属未来的 PF4J 阶段 change

## Capabilities

### New Capabilities

- `plugin-loading`：插件的打包约定、启动发现与加载、版本兼容判定、失败隔离、清单查询与禁用行为

### Modified Capabilities

（无——插件提供的实现经既有 SPI 注册表进入既有 API，不改变既有能力的规格行为，纯增量扩展）

## Impact

- **实现顺序**：本 change 依赖 `mvp-build-delivery-platform` 已实施（其 SPI 接口与注册表是插件的挂接点；对应其 design.md 的 D5/D7 决策），apply 顺序必须在其后
- **代码**：平台启动流程新增插件扫描阶段；新增扩展 API 版本常量与 manifest 解析；既有注册表支持编程式注册（替代仅组件扫描）
- **配置**：`application.yaml` 新增 `plugins.dir` 等配置项；仓库新增 `plugins/` 目录约定
- **构建**：新增示例插件子模块（shade 打包，产出可投放的插件 jar）
- **兼容性**：纯增量；无插件时平台行为与之前完全一致（空/缺失 plugins 目录正常启动）
- **假设记录**：本阶段为**重启加载**（放 jar 后需重启生效）；采用单一共享插件类加载器（插件间依赖冲突以"插件自带依赖需 shade"约定规避）——两者均为刻意的第一阶段简化
