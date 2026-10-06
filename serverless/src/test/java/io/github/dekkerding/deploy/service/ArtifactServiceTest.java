package io.github.dekkerding.deploy.service;

import io.github.dekkerding.deploy.domain.entity.ArtifactEntity;
import io.github.dekkerding.deploy.domain.mapper.ArtifactMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArtifactServiceTest {

    /** "hello" 的 sha256 十六进制 */
    private static final String HELLO_SHA256 =
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    private ArtifactService newService(Path storageDir, ArtifactMapper mapper) throws Exception {
        ArtifactService svc = new ArtifactService(mapper);
        Field f = ArtifactService.class.getDeclaredField("storageDir");
        f.setAccessible(true);
        f.set(svc, storageDir.toString());
        return svc;
    }

    @Test
    void 入库复制文件并登记sha256与大小(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("app.jar");
        Files.write(src, "hello".getBytes(StandardCharsets.UTF_8));
        ArtifactMapper mapper = mock(ArtifactMapper.class);
        when(mapper.insert(any(ArtifactEntity.class))).thenReturn(1);
        ArtifactService svc = newService(tmp.resolve("storage"), mapper);

        List<ArtifactEntity> saved = svc.ingest(7L, 3L, "demo", "1.0.0", Collections.singletonList(src));

        assertThat(saved).hasSize(1);
        ArtifactEntity a = saved.get(0);
        assertThat(a.getSha256()).isEqualTo(HELLO_SHA256);
        assertThat(a.getSizeBytes()).isEqualTo(5L);
        assertThat(a.getStoragePath()).isEqualTo("demo/1.0.0/app.jar");
        assertThat(a.getReleaseId()).isEqualTo(7L);
        assertThat(a.getProjectId()).isEqualTo(3L);
        // 物理文件已复制
        assertThat(tmp.resolve("storage").resolve(Paths.get("demo", "1.0.0", "app.jar"))).hasContent("hello");
    }

    @Test
    void 原生exe入库时声明宿主平台且非PORTABLE(@TempDir Path tmp) throws Exception {
        Path exe = tmp.resolve("agent.exe");
        Files.write(exe, new byte[]{0x4D, 0x5A, 0x00, 0x01});
        ArtifactMapper mapper = mock(ArtifactMapper.class);
        when(mapper.insert(any(ArtifactEntity.class))).thenReturn(1);
        ArtifactService svc = newService(tmp.resolve("storage"), mapper);

        List<ArtifactEntity> saved = svc.ingest(7L, 3L, "demo", "1.0.0", Collections.singletonList(exe));

        assertThat(saved).hasSize(1);
        ArtifactEntity a = saved.get(0);
        assertThat(a.getPortable()).isFalse();
        assertThat(a.getPlatformOs()).isEqualTo("windows");
        assertThat(a.getPlatformArch()).isEqualTo("x86_64");
        assertThat(a.getPlatformLibc()).isEqualTo("msvc");
    }

    @Test
    void 目录型产物被跳过(@TempDir Path tmp) throws Exception {
        Path dist = tmp.resolve("dist");
        Files.createDirectories(dist);
        ArtifactMapper mapper = mock(ArtifactMapper.class);
        ArtifactService svc = newService(tmp.resolve("storage"), mapper);

        List<ArtifactEntity> saved = svc.ingest(7L, 3L, "demo", "1.0.0", Collections.singletonList(dist));

        assertThat(saved).isEmpty();
        verify(mapper, times(0)).insert(any(ArtifactEntity.class));
    }

    @Test
    void 空产物列表直接返回(@TempDir Path tmp) throws Exception {
        ArtifactMapper mapper = mock(ArtifactMapper.class);
        ArtifactService svc = newService(tmp.resolve("storage"), mapper);
        assertThat(svc.ingest(7L, 3L, "demo", "1.0.0", null)).isEmpty();
        assertThat(svc.ingest(7L, 3L, "demo", "1.0.0", Collections.<Path>emptyList())).isEmpty();
    }

    @Test
    void 大文件sha256流式正确(@TempDir Path tmp) throws Exception {
        Path big = tmp.resolve("big.jar");
        byte[] data = new byte[1024 * 1024 + 17];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 31);
        }
        Files.write(big, data);
        assertThat(ArtifactService.sha256Of(big)).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(ArtifactService.sizeOf(big)).isEqualTo(data.length);
    }
}
