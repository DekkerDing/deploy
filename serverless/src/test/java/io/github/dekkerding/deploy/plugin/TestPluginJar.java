package io.github.dekkerding.deploy.plugin;

import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/** 测试插件 jar 工厂：内存编译实现类源码并打包 manifest + services 声明 + class。 */
public final class TestPluginJar {

    private TestPluginJar() {
    }

    /** 编译单个类源码为字节码（内存）；编译失败抛 IllegalStateException 附诊断。 */
    public static byte[] compile(String fqcn, String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("测试环境无系统编译器（需 JDK 而非 JRE）");
        }
        MemSource src = new MemSource(fqcn, source);
        MemSink sink = new MemSink();
        List<String> options = new ArrayList<>();
        // 平台类经 classpath 传入（编译需引用 BuildExecutor 等接口）
        options.add("-classpath");
        options.add(System.getProperty("java.class.path"));
        javax.tools.JavaCompiler.CompilationTask task = compiler.getTask(null,
                new javax.tools.ForwardingJavaFileManager<javax.tools.StandardJavaFileManager>(
                        compiler.getStandardFileManager(null, null, null)) {
                    @Override
                    public javax.tools.JavaFileObject getJavaFileForOutput(
                            javax.tools.JavaFileManager.Location location, String className,
                            javax.tools.JavaFileObject.Kind kind, javax.tools.FileObject sibling) {
                        return sink;
                    }
                }, null, options, null, java.util.Collections.singletonList(src));
        boolean ok = task.call();
        if (!ok || sink.bytes == null) {
            throw new IllegalStateException("测试插件源码编译失败: " + fqcn);
        }
        return sink.bytes;
    }

    /** 写插件 jar：manifest 属性 + services 声明（接口名→实现 fqcn，可指向不存在类）+ class 字节。 */
    public static void writeJar(File jar, Map<String, String> manifestAttrs,
                                Map<String, String> services, Map<String, byte[]> classes)
            throws IOException {
        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifestAttrs.forEach(mf.getMainAttributes()::putValue);
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar), mf)) {
            for (Map.Entry<String, String> e : services.entrySet()) {
                out.putNextEntry(new JarEntry("META-INF/services/" + e.getKey()));
                out.write(e.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.closeEntry();
            }
            for (Map.Entry<String, byte[]> e : classes.entrySet()) {
                out.putNextEntry(new JarEntry(e.getKey().replace('.', '/') + ".class"));
                out.write(e.getValue());
                out.closeEntry();
            }
        }
    }

    private static class MemSource extends SimpleJavaFileObject {
        private final String source;

        MemSource(String fqcn, String source) {
            super(URI.create("string:///" + fqcn.replace('.', '/') + ".java"), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    private static class MemSink extends SimpleJavaFileObject {
        byte[] bytes;

        MemSink() {
            super(URI.create("mem://out.class"), Kind.CLASS);
        }

        @Override
        public ByteArrayOutputStream openOutputStream() {
            return new ByteArrayOutputStream() {
                @Override
                public void close() throws IOException {
                    super.close();
                    bytes = toByteArray();
                }
            };
        }
    }
}
