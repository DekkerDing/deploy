package io.github.dekkerding.deploy.plugin;

import io.github.dekkerding.deploy.build.BuildExecutor;
import io.github.dekkerding.deploy.build.BuildExecutorRegistry;
import io.github.dekkerding.deploy.build.BuildResult;
import io.github.dekkerding.deploy.build.BuildType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 插件加载核心（tasks 2.2/2.3/3.1/3.2，specs/plugin-loading）：
 * 空目录/缺目录正常启动、manifest 缺属性判定、版本不兼容拒绝、坏插件三类隔离。
 * 真实场景（放 jar → 重启 → API 可见）在任务 5.2 端到端验证。
 */
class PluginLoadingTest {

    /** 自管临时目录（JUnit 5.8 无 CleanupMode.NEVER）：Windows 上 URLClassLoader 的 jar 句柄
     * 在进程内不可靠释放（生产=重启进程释放，与"重启期加载、不热卸载"设计一致），
     * 残留于 %TEMP% 无害，故不做目录删除。 */
    Path pluginsDir;

    private BuildExecutorRegistry executorRegistry;
    private PluginManager manager;

    @BeforeEach
    void setUp() throws java.io.IOException {
        pluginsDir = java.nio.file.Files.createTempDirectory("deploy-plugin-test");
        executorRegistry = new BuildExecutorRegistry(Collections.emptyList());
        PluginProperties props = new PluginProperties();
        props.setDir(pluginsDir.toString());
        manager = new PluginManager(props, executorRegistry,
                new io.github.dekkerding.deploy.delivery.DeliveryProviderRegistry(
                        Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        // Windows：URLClassLoader 持有 jar 句柄，关闭后 @TempDir 才能删除
        manager.shutdown();
    }

    // ---- 2.2 空目录与目录不存在 ----

    @Test
    void 空插件目录正常加载无失败记录() {
        manager.loadPlugins();
        assertThat(manager.getLoaded()).isEmpty();
        assertThat(manager.getFailures()).isEmpty();
    }

    @Test
    void 插件目录不存在正常加载无错误() {
        PluginProperties props = new PluginProperties();
        props.setDir(pluginsDir.resolve("not-exists").toString());
        PluginManager m = new PluginManager(props, executorRegistry,
                new io.github.dekkerding.deploy.delivery.DeliveryProviderRegistry(
                        Collections.emptyList()));
        m.loadPlugins();
        assertThat(m.getLoaded()).isEmpty();
        assertThat(m.getFailures()).isEmpty();
    }

    // ---- 4.2 禁用子目录 ----

    @Test
    void 禁用子目录内jar不加载() throws Exception {
        java.io.File disabled = pluginsDir.resolve("disabled").toFile();
        disabled.mkdirs();
        writeScriptPlugin(new java.io.File(disabled, "off-plugin.jar"), "off-plugin",
                "off.plugin.ScriptExecutorF", "1.0.0", "1.0");

        manager.loadPlugins();
        // 只扫插件目录顶层 *.jar：disabled 子目录内 jar 不进入加载也不记失败
        assertThat(manager.getLoaded()).isEmpty();
        assertThat(manager.getFailures()).isEmpty();
        assertThat(executorRegistry.supportedTypeNames()).doesNotContain("SCRIPT");
    }

    // ---- 合法插件全链路：加载 → 注册 → resolve 命中 ----

    @Test
    void 合法插件加载后类型经注册表枚举到() throws Exception {
        writeScriptPlugin(pluginsDir.resolve("good-plugin.jar").toFile(), "good-plugin", "good.plugin.ScriptExecutorA", "1.0.0", "1.0");

        manager.loadPlugins();

        assertThat(manager.getFailures()).isEmpty();
        assertThat(manager.getLoaded()).hasSize(1);
        PluginDescriptor d = manager.getLoaded().get(0);
        assertThat(d.getId()).isEqualTo("good-plugin");
        assertThat(d.getVersion()).isEqualTo("1.0.0");
        assertThat(d.getContributions()).containsEntry("BuildExecutor", 1);
        // 注册表可枚举到插件贡献的类型（任务 1.3 的"经 API 枚举到"数据源）
        assertThat(executorRegistry.supportedTypeNames()).contains("SCRIPT");
        BuildExecutor resolved = executorRegistry.resolve(BuildType.SCRIPT);
        assertThat(resolved.getClass().getName()).isEqualTo("good.plugin.ScriptExecutorA");
        assertThat(resolved.supports(BuildType.SCRIPT)).isTrue();
    }

    // ---- 2.3 manifest 缺属性 ----

    @Test
    void 缺PluginId判为无效插件() throws Exception {
        Map<String, String> manifest = baseManifest("1.0.0", "1.0");
        manifest.remove("Plugin-Id");
        TestPluginJar.writeJar(pluginsDir.resolve("no-id.jar").toFile(), manifest,
                services(BuildExecutor.class, "x.Missing"),
                Collections.emptyMap());

        manager.loadPlugins();
        assertThat(manager.getLoaded()).isEmpty();
        assertThat(manager.getFailures()).hasSize(1);
        assertThat(manager.getFailures().get(0).getReason()).contains("缺少 Plugin-Id");
    }

    @Test
    void 缺ExtensionApiVersion判为无效插件() throws Exception {
        Map<String, String> manifest = baseManifest("1.0.0", "1.0");
        manifest.remove("Extension-Api-Version");
        TestPluginJar.writeJar(pluginsDir.resolve("no-api.jar").toFile(), manifest,
                services(BuildExecutor.class, "x.Missing"),
                Collections.emptyMap());

        manager.loadPlugins();
        assertThat(manager.getFailures()).hasSize(1);
        assertThat(manager.getFailures().get(0).getReason()).contains("缺少 Extension-Api-Version");
    }

    // ---- 3.1 版本不兼容 ----

    @Test
    void 主版本不兼容被拒且错误含双方版本() throws Exception {
        writeScriptPlugin(pluginsDir.resolve("ver2-plugin.jar").toFile(), "ver2-plugin", "ver2.plugin.ScriptExecutorB", "1.0.0", "2.0");

        manager.loadPlugins();
        assertThat(manager.getLoaded()).isEmpty();
        assertThat(manager.getFailures()).hasSize(1);
        PluginFailure f = manager.getFailures().get(0);
        assertThat(f.getIdentifier()).isEqualTo("ver2-plugin");
        assertThat(f.getReason()).contains("2.0").contains(ExtensionApiVersion.CURRENT);
    }

    // ---- 3.2 失败隔离：坏插件与好插件并存 ----

    @Test
    void 损坏jar与好插件并存好插件成功损坏被记录() throws Exception {
        File broken = pluginsDir.resolve("broken.jar").toFile();
        try (FileOutputStream out = new FileOutputStream(broken)) {
            out.write("this is not a jar".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        writeScriptPlugin(pluginsDir.resolve("good2.jar").toFile(), "good2-plugin", "good2.plugin.ScriptExecutorC", "1.0.0", "1.0");

        manager.loadPlugins();
        assertThat(manager.getLoaded()).hasSize(1);
        assertThat(manager.getLoaded().get(0).getId()).isEqualTo("good2-plugin");
        assertThat(manager.getFailures()).hasSize(1);
        assertThat(manager.getFailures().get(0).getReason()).contains("无法读取");
        assertThat(executorRegistry.supportedTypeNames()).contains("SCRIPT");
    }

    @Test
    void 声明类缺失不阻止其他声明与插件() throws Exception {
        // 一个 jar 声明指向不存在的类，另一个 jar 是好插件
        TestPluginJar.writeJar(pluginsDir.resolve("missing-class.jar").toFile(),
                baseManifest("1.0.0", "1.0"),
                services(BuildExecutor.class, "no.such.ClassHere"),
                Collections.emptyMap());
        writeScriptPlugin(pluginsDir.resolve("still-good.jar").toFile(), "still-good-plugin", "stillgood.plugin.ScriptExecutorD", "1.0.0", "1.0");

        manager.loadPlugins();
        assertThat(manager.getLoaded()).hasSize(1);
        assertThat(manager.getFailures()).hasSize(1);
        assertThat(manager.getFailures().get(0).getReason()).contains("声明加载失败");
        assertThat(executorRegistry.supportedTypeNames()).contains("SCRIPT");
    }

    @Test
    void 实例化异常记录失败且启动不受影响() throws Exception {
        String fqcn = "boom.plugin.ScriptExecutorE";
        String src = "package boom.plugin;\n"
                + "public class ScriptExecutorE implements io.github.dekkerding.deploy.build.BuildExecutor {\n"
                + "  public ScriptExecutorE() { throw new IllegalStateException(\"插件构造故意失败\"); }\n"
                + "  public boolean supports(io.github.dekkerding.deploy.build.BuildType t) { return false; }\n"
                + "  public io.github.dekkerding.deploy.build.BuildResult execute(io.github.dekkerding.deploy.build.BuildContext c) { return null; }\n"
                + "}\n";
        TestPluginJar.writeJar(pluginsDir.resolve("boom.jar").toFile(),
                baseManifest("1.0.0", "1.0"),
                services(BuildExecutor.class, fqcn),
                Collections.singletonMap(fqcn, TestPluginJar.compile(fqcn, src)));

        manager.loadPlugins();
        assertThat(manager.getLoaded()).isEmpty();
        assertThat(manager.getFailures()).hasSize(1);
        assertThat(manager.getFailures().get(0).getReason()).contains("boom.plugin.ScriptExecutorE");
    }

    // ---- helpers ----

    private static Map<String, String> services(Class<?> iface, String implFqcn) {
        Map<String, String> s = new LinkedHashMap<>();
        s.put(iface.getName(), implFqcn);
        return s;
    }

    private static Map<String, String> baseManifest(String version, String apiVersion) {
        Map<String, String> m = new HashMap<>();
        m.put("Plugin-Id", "test-plugin");
        m.put("Plugin-Version", version);
        m.put("Extension-Api-Version", apiVersion);
        return m;
    }

    /** 写一个合法最小插件：SCRIPT 类型 BuildExecutor（成功返回 stub BuildResult）。 */
    private static void writeScriptPlugin(File jar, String pluginId, String fqcn,
                                          String version, String apiVersion) throws Exception {
        String pkg = fqcn.substring(0, fqcn.lastIndexOf('.'));
        String cls = fqcn.substring(fqcn.lastIndexOf('.') + 1);
        String src = "package " + pkg + ";\n"
                + "public class " + cls + " implements io.github.dekkerding.deploy.build.BuildExecutor {\n"
                + "  public boolean supports(io.github.dekkerding.deploy.build.BuildType t) { return t == io.github.dekkerding.deploy.build.BuildType.SCRIPT; }\n"
                + "  public io.github.dekkerding.deploy.build.BuildResult execute(io.github.dekkerding.deploy.build.BuildContext c) {\n"
                + "    return io.github.dekkerding.deploy.build.BuildResult.ok(0, java.util.Collections.emptyList());\n"
                + "  }\n"
                + "}\n";
        Map<String, String> manifest = baseManifest(version, apiVersion);
        manifest.put("Plugin-Id", pluginId);
        TestPluginJar.writeJar(jar, manifest,
                services(BuildExecutor.class, fqcn),
                Collections.singletonMap(fqcn, TestPluginJar.compile(fqcn, src)));
    }
}
