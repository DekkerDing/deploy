package io.github.dekkerding.deploy.plugin;

import io.github.dekkerding.deploy.build.BuildExecutor;
import io.github.dekkerding.deploy.build.BuildExecutorRegistry;
import io.github.dekkerding.deploy.delivery.DeliveryProvider;
import io.github.dekkerding.deploy.delivery.DeliveryProviderRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.jar.Attributes;

/**
 * 插件管理器（design D1/D2/D5/D6）：启动期扫描插件目录，经单一共享 URLClassLoader
 * + ServiceLoader 加载扩展实现并编程式注册进既有注册表。
 *
 * 失败隔离三层捕获（spec：坏插件零爆炸半径，启动永不因插件失败而失败）：
 * jar 读取 → manifest 属性 → 版本兼容 → ServiceLoader 逐声明迭代。
 */
@Slf4j
@Component
public class PluginManager implements ApplicationRunner {

    private final PluginProperties props;
    private final BuildExecutorRegistry executorRegistry;
    private final DeliveryProviderRegistry providerRegistry;

    private final List<PluginDescriptor> loaded = new ArrayList<>();
    private final List<PluginFailure> failures = new ArrayList<>();
    private URLClassLoader pluginLoader;

    public PluginManager(PluginProperties props,
                         BuildExecutorRegistry executorRegistry,
                         DeliveryProviderRegistry providerRegistry) {
        this.props = props;
        this.executorRegistry = executorRegistry;
        this.providerRegistry = providerRegistry;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            loadPlugins();
        } catch (Exception e) {
            // 兜底（D6）：插件阶段的任何异常不阻止平台启动
            log.error("插件加载阶段异常（平台继续启动）: {}", e.getMessage(), e);
        }
    }

    /** 扫描并加载；目录不存在/为空时静默通过（无插件行为与之前一致）。 */
    synchronized void loadPlugins() {
        loaded.clear();
        failures.clear();
        File dir = new File(props.getDir());
        File[] jars = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jar"));
        if (!dir.isDirectory() || jars == null || jars.length == 0) {
            log.info("插件目录无插件（{}），跳过加载", dir.getAbsolutePath());
            return;
        }

        // 阶段一：逐 jar 校验（读取/manifest/版本），通过的进入共享类加载器
        Map<String, File> validById = new LinkedHashMap<>();
        Map<String, Boolean> hasDeclaration = new LinkedHashMap<>();
        List<URL> urls = new ArrayList<>();
        for (File jar : jars) {
            Manifest mf;
            boolean declared;
            try (JarFile jf = new JarFile(jar)) {
                mf = jf.getManifest();
                declared = hasSupportedDeclaration(jf);
            } catch (IOException e) {
                failures.add(new PluginFailure(jar.getName(),
                        "jar 无法读取（损坏或非 jar）: " + e.getMessage()));
                continue;
            }
            if (mf == null) {
                failures.add(new PluginFailure(jar.getName(), "缺少 manifest"));
                continue;
            }
            Attributes at = mf.getMainAttributes();
            String id = at.getValue("Plugin-Id");
            String version = at.getValue("Plugin-Version");
            String apiVersion = at.getValue("Extension-Api-Version");
            if (id == null || id.trim().isEmpty()) {
                failures.add(new PluginFailure(jar.getName(), "缺少 Plugin-Id"));
                continue;
            }
            if (version == null || version.trim().isEmpty()) {
                failures.add(new PluginFailure(id, "缺少 Plugin-Version"));
                continue;
            }
            if (apiVersion == null || apiVersion.trim().isEmpty()) {
                failures.add(new PluginFailure(id, "缺少 Extension-Api-Version"));
                continue;
            }
            if (!ExtensionApiVersion.compatible(apiVersion)) {
                failures.add(new PluginFailure(id, "扩展 API 版本不兼容: 插件声明 " + apiVersion
                        + "，平台实际 " + ExtensionApiVersion.CURRENT
                        + "（要求主版本相等且插件次版本不高于平台）"));
                continue;
            }
            if (validById.containsKey(id)) {
                failures.add(new PluginFailure(id, "重复 Plugin-Id（jar " + jar.getName() + "）"));
                continue;
            }
            validById.put(id, jar);
            hasDeclaration.put(id, declared);
            try {
                urls.add(jar.toURI().toURL());
            } catch (IOException e) {
                failures.add(new PluginFailure(id, "jar URL 构造失败: " + e.getMessage()));
            }
        }
        if (validById.isEmpty()) {
            return;
        }

        // 阶段二：单一共享类加载器（D2），ServiceLoader 逐声明加载并按实现类归属 jar
        // （JDK8：URLClassLoader 无命名构造器）
        pluginLoader = new URLClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader());
        Map<String, Map<String, Integer>> contribByJar = new LinkedHashMap<>();
        loadExtensionPoint(BuildExecutor.class, executorRegistry::register, contribByJar);
        loadExtensionPoint(DeliveryProvider.class, providerRegistry::register, contribByJar);

        for (Map.Entry<String, File> e : validById.entrySet()) {
            Map<String, Integer> contrib = contribByJar.get(e.getValue().getName());
            if (contrib == null || contrib.isEmpty()) {
                if (Boolean.TRUE.equals(hasDeclaration.get(e.getKey()))) {
                    // 有声明但逐条加载失败：失败已在声明层记录，此处不重复记
                    continue;
                }
                failures.add(new PluginFailure(e.getKey(),
                        "无受支持扩展点的 META-INF/services 声明"));
                continue;
            }
            loaded.add(new PluginDescriptor(e.getKey(),
                    manifestValue(e.getValue(), "Plugin-Version"),
                    e.getValue().getName(), contrib));
        }
        log.info("插件加载完成: 成功 {} 个，失败 {} 个", loaded.size(), failures.size());
        failures.forEach(f -> log.warn("插件失败: {} — {}", f.getIdentifier(), f.getReason()));
    }

    /** 对一个扩展点执行 ServiceLoader 加载：逐声明捕获（D6），实现类按 codeSource 归属 jar。 */
    private <T> void loadExtensionPoint(Class<T> ext, java.util.function.Consumer<T> register,
                                        Map<String, Map<String, Integer>> contribByJar) {
        java.util.Iterator<T> it = java.util.ServiceLoader.load(ext, pluginLoader).iterator();
        while (true) {
            T impl;
            try {
                if (!it.hasNext()) {
                    break;
                }
                impl = it.next();
            } catch (ServiceConfigurationError err) {
                // 声明的类缺失/无法实例化/格式错误：逐声明记录，不中断其他声明
                failures.add(new PluginFailure(ext.getSimpleName(),
                        "声明加载失败: " + err.getMessage()));
                continue;
            }
            try {
                register.accept(impl);
                String jarName = jarOf(impl);
                contribByJar.computeIfAbsent(jarName, k -> new LinkedHashMap<>())
                        .merge(ext.getSimpleName(), 1, Integer::sum);
            } catch (Throwable t) {
                failures.add(new PluginFailure(ext.getSimpleName(),
                        "注册失败（" + impl.getClass().getName() + "）: " + t));
            }
        }
    }

    /** jar 是否含受支持扩展点的 META-INF/services 声明条目（区分"无声明"与"声明加载失败"）。 */
    private static boolean hasSupportedDeclaration(JarFile jf) {
        java.util.Enumeration<java.util.jar.JarEntry> entries = jf.entries();
        String prefix = "META-INF/services/";
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (name.startsWith(prefix)
                    && (name.endsWith(BuildExecutor.class.getName())
                    || name.endsWith(DeliveryProvider.class.getName()))) {
                return true;
            }
        }
        return false;
    }

    /** 实现类所在 jar 文件名（归属贡献计数）；无法判定时归为 "unknown"。 */
    private static String jarOf(Object impl) {
        try {
            java.security.ProtectionDomain pd = impl.getClass().getProtectionDomain();
            if (pd != null && pd.getCodeSource() != null && pd.getCodeSource().getLocation() != null) {
                String loc = pd.getCodeSource().getLocation().toString();
                int slash = Math.max(loc.lastIndexOf('/'), loc.lastIndexOf('\\'));
                return slash >= 0 ? loc.substring(slash + 1) : loc;
            }
        } catch (Throwable ignored) {
            // 安全管理器受限等场景归 unknown
        }
        return "unknown";
    }

    private static String manifestValue(File jar, String key) {
        try (JarFile jf = new JarFile(jar)) {
            String v = jf.getManifest().getMainAttributes().getValue(key);
            return v == null ? "?" : v;
        } catch (IOException e) {
            return "?";
        }
    }

    public List<PluginDescriptor> getLoaded() {
        return new ArrayList<>(loaded);
    }

    public List<PluginFailure> getFailures() {
        return new ArrayList<>(failures);
    }

    @PreDestroy
    public void shutdown() {
        if (pluginLoader != null) {
            try {
                pluginLoader.close();
            } catch (IOException ignored) {
                // 关闭期尽力而为
            }
            pluginLoader = null;
        }
    }
}
