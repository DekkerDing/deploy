package io.github.dekkerding.deploy.plugin;

/**
 * 扩展 API 版本（design D3/D4）：平台扩展点契约的版本标识。
 *
 * 兼容判定：主版本相等且插件声明的次版本 ≤ 平台提供的次版本——
 * 主版本差异 = 接口破坏性变更；平台次版本更高 = 向后兼容的新能力，旧插件可用。
 * 随扩展点接口的破坏性变更手动提升主版本。
 */
public final class ExtensionApiVersion {

    /** 平台当前扩展 API 版本（major.minor）。 */
    public static final String CURRENT = "1.0";

    private ExtensionApiVersion() {
    }

    /** 判定插件声明版本是否与平台兼容（design D4：major 相等 && 插件 minor ≤ 平台 minor）。 */
    public static boolean compatible(String declared) {
        return compatible(declared, CURRENT);
    }

    /** 判定核心（包可见：单测可注入平台版本验证规则本身）。 */
    static boolean compatible(String declared, String platform) {
        int[] d = parse(declared);
        int[] c = parse(platform);
        return d != null && c != null && d[0] == c[0] && d[1] <= c[1];
    }

    /** 解析 "major.minor"；畸形（缺段/非数字）返回 null，由调用方按无效插件处理。 */
    static int[] parse(String version) {
        if (version == null) {
            return null;
        }
        String[] parts = version.trim().split("\\.");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
