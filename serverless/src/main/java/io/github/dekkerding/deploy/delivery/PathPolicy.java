package io.github.dekkerding.deploy.delivery;

import org.springframework.stereotype.Component;

/**
 * 跨 OS 安装路径策略（design D8）：
 * linux → /opt/<项目>/<版本>/ ；windows → C:\apps\<项目>\<版本>\
 * 每版本独立目录（旧版本保留，供回滚切换，不做覆盖式升级）。
 * 项目名/版本中的路径分隔符与非法字符做净化，防止越出策略根目录。
 */
@Component
public class PathPolicy {

    /** 生成目标机安装目录（带尾分隔符）。 */
    public String installDir(String targetOs, String projectName, String version) {
        String project = sanitize(projectName);
        String ver = sanitize(version);
        if ("windows".equalsIgnoreCase(targetOs)) {
            return "C:\\apps\\" + project + "\\" + ver + "\\";
        }
        // 类 Unix 一律 /opt（freebsd/openbsd 同样适用）
        return "/opt/" + project + "/" + ver + "/";
    }

    /** 制品在安装目录内的落点文件全路径。 */
    public String artifactTargetPath(String targetOs, String projectName, String version, String fileName) {
        return installDir(targetOs, projectName, version) + sanitize(fileName);
    }

    /** 净化：去掉路径分隔符与 Windows 保留字符，空白转下划线。 */
    static String sanitize(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "unknown";
        }
        String s = raw.trim().replaceAll("[/\\\\]", "_");
        s = s.replaceAll("[:*?\"<>|]", "-");
        s = s.replaceAll("\\s+", "_");
        return s;
    }
}
