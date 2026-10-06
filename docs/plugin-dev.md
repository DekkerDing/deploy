# 插件开发指南（plugin-system 第一阶段）

平台扩展点：`BuildExecutor`（构建执行器）与 `DeliveryProvider`（交付提供者）。
插件 = 一个 jar，放入 `plugins/` 目录后**重启平台**即生效（重启期加载，不做热插拔）。

## 快速开始

参照仓库 `serverless/example-plugin/`（最小可用插件），四步：

1. **依赖平台接口（provided 方式，不打包进插件）**

   ```groovy
   plugins { id 'java' }
   dependencies {
       compileOnly project(':')   // 或以坐标引用 deploy-platform 的 plain jar
   }
   ```

2. **实现扩展点**（普通 Java 类，无 Spring 依赖——插件实例不是 Spring Bean）：

   ```java
   public class MyExecutor implements BuildExecutor {
       public boolean supports(BuildType t) { return t == BuildType.SCRIPT; }
       public BuildResult execute(BuildContext ctx) { /* ... */ }
   }
   ```

3. **声明扩展点实现**（ServiceLoader 标准）：`src/main/resources/META-INF/services/io.github.dekkerding.deploy.build.BuildExecutor`，内容为实现类全限定名（每行一个）。

4. **manifest 注入三属性**（jar 构建期自动写入）：

   ```groovy
   jar {
       manifest {
           attributes(
               'Plugin-Id': 'my-executor',            // 唯一标识（重复拒绝加载）
               'Plugin-Version': '1.0.0',
               'Extension-Api-Version': '1.0',        // 平台当前 1.0
           )
       }
   }
   ```

`./gradlew :example-plugin:build` → 产物 jar 投入 `plugins/` → 重启 →
`GET /api/meta` 出现贡献的能力，`GET /api/plugins` 查看加载状态。

## 打包约定

- **插件自带依赖必须 shade 进插件 jar**（单一共享类加载器，design D2）。
  示例插件零第三方依赖故无需 shade；引入依赖时用 shadow 插件：

  ```groovy
  plugins {
      id 'com.github.johnrengelman.shadow' version '7.1.2'  // JDK8 兼容线
      id 'java'
  }
  // shadowJar 产物即插件 jar（manifest 属性同样在 jar{} 块配置，
  // shadow 默认合并 manifest；shadowJar 任务继承之）
  ```

- 不重复打包平台类（`compileOnly`），否则与平台版本冲突行为不可预测。
- 插件间同名类冲突显式接受：冲突规避责任在插件作者（改包名/shade 重定位）。

## 版本兼容

- `Extension-Api-Version` 采用 `主版本.次版本`。
- 判定规则：**主版本相等，且插件声明的次版本 ≤ 平台提供的次版本**。
  不兼容的插件被拒绝加载，`GET /api/plugins` 的 `failures` 列出
  插件 id、声明版本与平台实际版本。
- 平台扩展点接口发生破坏性变更时提升主版本；仅新增方法（带 default 实现）提升次版本。

## 信任边界（安全）

- **插件目录即信任边界**：任何放入 `plugins/` 的 jar 都会在平台进程内执行任意代码
  （与 Jenkins 插件同一信任模型）。该目录只应由管理员写放。
- 平台 API 处于过渡认证阶段时（详见 MVP design D10），尤其不要把 plugins/ 目录
  暴露给不受控的写入途径。

## 禁用与卸载

- **禁用**：把 jar 移入 `plugins/disabled/` 子目录，重启后不再加载
  （不删除文件，随时移回恢复）。
- **卸载**：删除 jar 并重启。
- 配置项（`serverless/src/main/resources/application.yaml`）：

  ```yaml
  plugins:
      dir: ./plugins          # 插件目录（默认 ./plugins）
      disabled-subdir: disabled
  ```

## 故障排查

- `GET /api/plugins` 的 `failures` 无需看日志即可定位：
  - `jar 无法读取` —— 文件损坏或不是 jar
  - `缺少 Plugin-Id / Plugin-Version / Extension-Api-Version` —— manifest 属性不全
  - `扩展 API 版本不兼容` —— 附声明版本与平台版本
  - `声明加载失败: ... Provider X not found / could not be instantiated` ——
    services 声明的类缺失或构造抛异常
  - `无受支持扩展点的 META-INF/services 声明` —— jar 里没有受支持扩展点的声明文件
- 单个坏插件不影响其他插件与平台启动（specs/plugin-loading 失败隔离）。
