package io.github.dekkerding.deploy.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * JAR 字节码与目标 JVM 预检（design D6：windows/arm64 无 JDK8 原生实现等场景前置拦截）。
 * class 文件 major version = 目标 JVM 大版本 + 44（8→52，11→55，17→61）。
 * 预检失败在部署前拒绝，避免上传后目标机起不来。
 */
@Component
public class JvmCompatPrecheck {

    public static class Result {
        public final boolean ok;
        public final String message;
        public final int maxMajor;

        Result(boolean ok, String message, int maxMajor) {
            this.ok = ok;
            this.message = message;
            this.maxMajor = maxMajor;
        }
    }

    /** 扫描 jar 内所有 .class 的最大 major version，与目标 JVM 比较。 */
    public Result precheck(Path jar, int targetJvmVersion) {
        int maxMajor;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            maxMajor = scanMaxMajor(zip);
        } catch (IOException e) {
            return new Result(false, "JAR 无法读取，预检失败: " + jar + " (" + e.getMessage() + ")", -1);
        }
        if (maxMajor < 0) {
            return new Result(true, "JAR 内无 class 文件，跳过字节码预检", -1);
        }
        int targetMajor = 44 + targetJvmVersion;
        if (maxMajor <= targetMajor) {
            return new Result(true, "字节码兼容: class major " + maxMajor + "（Java "
                    + (maxMajor - 44) + "）≤ 目标 JVM " + targetJvmVersion + "（major " + targetMajor + "）", maxMajor);
        }
        return new Result(false, "字节码不兼容: JAR 需要 Java " + (maxMajor - 44)
                + "（class major " + maxMajor + "），目标 JVM 仅 " + targetJvmVersion
                + "（major " + targetMajor + "），部署前拒绝", maxMajor);
    }

    private static int scanMaxMajor(ZipFile zip) throws IOException {
        int max = -1;
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if (e.isDirectory() || !e.getName().endsWith(".class")) {
                continue;
            }
            try (InputStream in = zip.getInputStream(e)) {
                byte[] head = new byte[8];
                int read = 0;
                while (read < 8) {
                    int n = in.read(head, read, 8 - read);
                    if (n < 0) {
                        break;
                    }
                    read += n;
                }
                if (read == 8 && (head[0] & 0xFF) == 0xCA && (head[1] & 0xFF) == 0xFE
                        && (head[2] & 0xFF) == 0xBA && (head[3] & 0xFF) == 0xBE) {
                    int major = ((head[6] & 0xFF) << 8) | (head[7] & 0xFF);
                    if (major > max) {
                        max = major;
                    }
                }
            }
        }
        return max;
    }
}
