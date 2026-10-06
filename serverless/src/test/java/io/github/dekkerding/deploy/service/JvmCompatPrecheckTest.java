package io.github.dekkerding.deploy.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class JvmCompatPrecheckTest {

    private final JvmCompatPrecheck precheck = new JvmCompatPrecheck();

    /** 伪造指定 major version 的 class 文件字节（仅头部 8 字节被读取）。 */
    private static byte[] fakeClass(int major) {
        return new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
                0, 0, (byte) (major >> 8), (byte) major, 0, 0};
    }

    private static Path jarWith(@TempDir Path tmp, String name, Object... entryNameAndMajors) throws IOException {
        Path jar = tmp.resolve(name);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jar))) {
            zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zos.write("Manifest-Version: 1.0\n".getBytes());
            zos.closeEntry();
            for (int i = 0; i < entryNameAndMajors.length; i += 2) {
                zos.putNextEntry(new ZipEntry((String) entryNameAndMajors[i]));
                zos.write(fakeClass((Integer) entryNameAndMajors[i + 1]));
                zos.closeEntry();
            }
        }
        return jar;
    }

    @Test
    void JDK8字节码到JVM8通过(@TempDir Path tmp) throws Exception {
        Path jar = jarWith(tmp, "app8.jar", "com/A.class", 52);
        JvmCompatPrecheck.Result r = precheck.precheck(jar, 8);
        assertThat(r.ok).isTrue();
        assertThat(r.maxMajor).isEqualTo(52);
        assertThat(r.message).contains("Java 8").contains("≤");
    }

    @Test
    void 高版本字节码到JVM8部署前拒绝(@TempDir Path tmp) throws Exception {
        Path jar = jarWith(tmp, "app11.jar", "com/A.class", 55);
        JvmCompatPrecheck.Result r = precheck.precheck(jar, 8);
        assertThat(r.ok).isFalse();
        assertThat(r.message).contains("Java 11").contains("目标 JVM 仅 8").contains("拒绝");
    }

    @Test
    void 多class取最大major(@TempDir Path tmp) throws Exception {
        Path jar = jarWith(tmp, "mix.jar", "com/A.class", 52, "com/B.class", 61);
        JvmCompatPrecheck.Result r = precheck.precheck(jar, 17);
        assertThat(r.maxMajor).isEqualTo(61);
        assertThat(r.ok).isTrue(); // 61 ≤ 44+17=61
        assertThat(precheck.precheck(jar, 11).ok).isFalse(); // 61 > 55
    }

    @Test
    void 无class的jar跳过预检(@TempDir Path tmp) throws Exception {
        Path jar = jarWith(tmp, "empty.jar");
        JvmCompatPrecheck.Result r = precheck.precheck(jar, 8);
        assertThat(r.ok).isTrue();
        assertThat(r.message).contains("跳过");
    }

    @Test
    void 非jar文件预检失败(@TempDir Path tmp) throws Exception {
        Path notJar = tmp.resolve("broken.jar");
        Files.write(notJar, "not a zip".getBytes());
        assertThat(precheck.precheck(notJar, 8).ok).isFalse();
    }
}
