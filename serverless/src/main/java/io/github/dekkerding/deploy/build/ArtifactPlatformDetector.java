package io.github.dekkerding.deploy.build;

import java.util.Locale;

/**
 * 制品平台描述符检测（design D6：PORTABLE vs PLATFORM_BOUND）。
 *
 * - 纯 JVM 制品（.jar）：PORTABLE —— 平台三字段为空，可路由到任意 os/arch
 *   （目标侧 JVM 字节码兼容性预检在路由阶段做，任务 5.4）。
 * - 原生制品（.exe/.dll/.so/.msi 等）与未知类型：保守 PLATFORM_BOUND，
 *   声明为构建宿主平台（os/arch 取自系统属性归一化）。
 */
public final class ArtifactPlatformDetector {

    private ArtifactPlatformDetector() {
    }

    /** 制品平台描述符。portable=true 时三字段为 null。 */
    public static class Descriptor {
        public final boolean portable;
        public final String os;
        public final String arch;
        public final String libc;

        Descriptor(boolean portable, String os, String arch, String libc) {
            this.portable = portable;
            this.os = os;
            this.arch = arch;
            this.libc = libc;
        }

        public static Descriptor portable() {
            return new Descriptor(true, null, null, null);
        }
    }

    /** 判定为纯 JVM 制品的扩展名。 */
    private static boolean isJvmArtifact(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".war");
    }

    public static Descriptor detect(String fileName) {
        if (isJvmArtifact(fileName)) {
            return Descriptor.portable();
        }
        // 原生/未知制品：绑定构建宿主平台（保守策略，防止误路由）
        return new Descriptor(false, hostOs(), hostArch(), hostLibc());
    }

    /** os.name 归一化：windows / linux / darwin / 其他原样小写。 */
    static String hostOs() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "windows";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "darwin";
        }
        return os.isEmpty() ? "unknown" : os.split("\\s+")[0];
    }

    /** os.arch 归一化：x86_64 / arm64 / 其余原样小写。 */
    static String hostArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            return "x86_64";
        }
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return "arm64";
        }
        return arch.isEmpty() ? "unknown" : arch;
    }

    /** libc 粗判：windows→msvc，apple→libc 统称，其余 glibc（musl 检测留给目标探针）。 */
    static String hostLibc() {
        String os = hostOs();
        if ("windows".equals(os)) {
            return "msvc";
        }
        if ("darwin".equals(os)) {
            return "libSystem";
        }
        return "glibc";
    }
}
