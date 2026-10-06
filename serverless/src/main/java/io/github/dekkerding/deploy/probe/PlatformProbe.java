package io.github.dekkerding.deploy.probe;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 目标平台探针（design D7）：解析目标机命令输出为平台描述符。
 *
 * - 类 Unix：`uname -sm` → "Linux x86_64" / "Darwin arm64" / "FreeBSD amd64" ...
 * - Windows：环境变量 PROCESSOR_ARCHITECTURE（AMD64 / ARM64 / x86）
 * - libc 细化（可选）：`ldd --version` 输出含 musl → musl，否则 linux 默认 glibc
 *
 * 无法识别 → known=false（probe_status=UNKNOWN），交付被阻止直至人工确认。
 * 探针在目标机执行的实际下发由交付通道（任务组 6）完成，本类只负责解析与本地探测。
 */
@Component
public class PlatformProbe {

    /** 探针结果：known=false 表示无法识别，os/arch 为原始串。 */
    public static class ProbeResult {
        public final String os;
        public final String arch;
        public final String libc;
        public final boolean known;

        ProbeResult(String os, String arch, String libc, boolean known) {
            this.os = os;
            this.arch = arch;
            this.libc = libc;
            this.known = known;
        }

        public static ProbeResult unknown(String rawOs, String rawArch) {
            return new ProbeResult(rawOs, rawArch, null, false);
        }
    }

    /** 已识别的 os 规范名（白名单之外 → UNKNOWN，交付被阻止直至人工确认）。 */
    private static final java.util.Set<String> KNOWN_OS = java.util.Collections.unmodifiableSet(
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "linux", "windows", "darwin", "freebsd", "openbsd", "netbsd")));

    /** 已识别的 arch 规范名。 */
    private static final java.util.Set<String> KNOWN_ARCH = java.util.Collections.unmodifiableSet(
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "amd64", "arm64", "386", "armv7")));

    /** 解析 `uname -sm` 输出（如 "Linux x86_64\r\n"）。 */
    public ProbeResult parseUname(String unameSmOutput) {
        if (unameSmOutput == null) {
            return ProbeResult.unknown(null, null);
        }
        String[] parts = unameSmOutput.trim().split("\\s+");
        if (parts.length < 2) {
            return ProbeResult.unknown(unameSmOutput.trim(), null);
        }
        String os = normalizeOs(parts[0]);
        String arch = normalizeArch(parts[1]);
        boolean known = KNOWN_OS.contains(os) && KNOWN_ARCH.contains(arch);
        String libc = known ? defaultLibc(os) : null;
        return new ProbeResult(os, arch, libc, known);
    }

    private static String defaultLibc(String os) {
        if ("linux".equals(os)) {
            return "glibc";
        }
        if ("windows".equals(os)) {
            return "msvc";
        }
        return "darwin".equals(os) ? "libSystem" : null;
    }

    /** 解析 Windows PROCESSOR_ARCHITECTURE 值。 */
    public ProbeResult parseWindowsArch(String processorArchitecture) {
        if (processorArchitecture == null) {
            return ProbeResult.unknown("windows", null);
        }
        String v = processorArchitecture.trim().toUpperCase(Locale.ROOT);
        String arch;
        switch (v) {
            case "AMD64":
                arch = "amd64";
                break;
            case "ARM64":
                arch = "arm64";
                break;
            case "X86":
            case "IA32":
            case "IA64":
                arch = "386";
                break;
            default:
                arch = "unknown";
        }
        boolean known = !"unknown".equals(arch);
        return new ProbeResult("windows", known ? arch : v, known ? "msvc" : null, known);
    }

    /** `ldd --version` 输出细化 libc：含 "musl" → musl，其余 linux 默认 glibc。 */
    public String libcFromLdd(String lddVersionOutput) {
        if (lddVersionOutput != null && lddVersionOutput.toLowerCase(Locale.ROOT).contains("musl")) {
            return "musl";
        }
        return "glibc";
    }

    /** 本机探测（开发机自举路径，任务 7.1 使用）。 */
    public ProbeResult local() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return parseWindowsArch(System.getenv("PROCESSOR_ARCHITECTURE"));
        }
        // 类 Unix 本机：直接读系统属性（等价 uname 语义）
        return parseUname(osName + " " + System.getProperty("os.arch", ""));
    }

    private static String normalizeOs(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) {
            return "unknown";
        }
        if (s.equals("darwin")) {
            return "darwin";
        }
        // "Linux"→linux、"FreeBSD"→freebsd、"GNU/Linux"→linux
        if (s.contains("linux")) {
            return "linux";
        }
        if (s.endsWith("bsd")) {
            return s; // freebsd/openbsd/netbsd
        }
        return s;
    }

    private static String normalizeArch(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        switch (s) {
            case "x86_64":
            case "amd64":
                return "amd64";
            case "aarch64":
            case "arm64":
                return "arm64";
            case "i386":
            case "i486":
            case "i586":
            case "i686":
            case "x86":
                return "386";
            case "armv7l":
            case "armv6l":
                return "armv7";
            default:
                return s.isEmpty() ? "unknown" : s;
        }
    }
}
