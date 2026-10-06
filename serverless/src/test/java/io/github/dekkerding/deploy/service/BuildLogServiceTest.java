package io.github.dekkerding.deploy.service;

import io.github.dekkerding.deploy.domain.entity.ReleaseEntity;
import io.github.dekkerding.deploy.domain.mapper.ReleaseMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 直接实例化测试（不起 Spring 容器），用反射注入 logDir、Mockito stub mapper。 */
class BuildLogServiceTest {

    private BuildLogService newServiceWithDir(Path dir, long releaseId) throws Exception {
        ReleaseMapper mapper = mock(ReleaseMapper.class);
        ReleaseEntity release = new ReleaseEntity();
        release.setId(releaseId);
        release.setState("BUILT");
        when(mapper.selectById(anyLong())).thenReturn(release);
        BuildLogService svc = new BuildLogService(mapper);
        Field f = BuildLogService.class.getDeclaredField("logDir");
        f.setAccessible(true);
        f.set(svc, dir.toString());
        return svc;
    }

    @Test
    void 偏移量续读能拼回完整内容(@TempDir Path tmp) throws Exception {
        Path log = tmp.resolve("1").resolve("build.log");
        Files.createDirectories(log.getParent());
        String text = "line1: BUILD START\nline2: javac ok\nline3: BUILD SUCCESS\n";
        Files.write(log, text.getBytes(StandardCharsets.UTF_8));
        BuildLogService svc = newServiceWithDir(tmp, 1L);

        StringBuilder assembled = new StringBuilder();
        long offset = 0;
        int rounds = 0;
        do {
            BuildLogService.LogChunk c = svc.read(1L, offset);
            assertThat(c.isExists()).isTrue();
            assembled.append(c.getContent());
            offset = c.getNextOffset();
            assertThat(c.getSize()).isEqualTo(text.getBytes(StandardCharsets.UTF_8).length);
        } while (offset < text.getBytes(StandardCharsets.UTF_8).length && ++rounds < 100);

        assertThat(assembled.toString()).isEqualTo(text);
        assertThat(offset).isEqualTo(text.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void 超过单次上限时截断且truncated为真(@TempDir Path tmp) throws Exception {
        Path log = tmp.resolve("2").resolve("build.log");
        Files.createDirectories(log.getParent());
        byte[] big = new byte[BuildLogService.MAX_CHUNK_BYTES + 100];
        for (int i = 0; i < big.length; i++) {
            big[i] = 'x';
        }
        Files.write(log, big);
        BuildLogService svc = newServiceWithDir(tmp, 1L);

        BuildLogService.LogChunk c = svc.read(2L, 0);
        assertThat(c.isTruncated()).isTrue();
        assertThat(c.getContent().length()).isEqualTo(BuildLogService.MAX_CHUNK_BYTES);
        assertThat(c.getNextOffset()).isEqualTo(BuildLogService.MAX_CHUNK_BYTES);

        BuildLogService.LogChunk tail = svc.read(2L, c.getNextOffset());
        assertThat(tail.isTruncated()).isFalse();
        assertThat(tail.getContent().length()).isEqualTo(100);
    }

    @Test
    void 多字节字符在块边界被安全替换不崩溃(@TempDir Path tmp) throws Exception {
        Path log = tmp.resolve("3").resolve("build.log");
        Files.createDirectories(log.getParent());
        // “构建”一个中文词占 6 字节；从第 1 字节处切开必然撕裂一个多字节字符
        Files.write(log, "构建日志".getBytes(StandardCharsets.UTF_8));
        BuildLogService svc = newServiceWithDir(tmp, 1L);

        BuildLogService.LogChunk c = svc.read(3L, 1);
        assertThat(c.isExists()).isTrue();
        assertThat(c.getContent()).doesNotContain("构"); // 被撕裂的首字符不能完好出现
        assertThat(c.getNextOffset()).isGreaterThan(1);
    }

    @Test
    void 文件不存在时exists为假_offset归零(@TempDir Path tmp) throws Exception {
        BuildLogService svc = newServiceWithDir(tmp, 1L);
        BuildLogService.LogChunk c = svc.read(9L, 0);
        assertThat(c.isExists()).isFalse();
        assertThat(c.getContent()).isEmpty();
        assertThat(c.getNextOffset()).isEqualTo(0);
        assertThat(c.getSize()).isEqualTo(0);
    }

    @Test
    void 负偏移被拒绝(@TempDir Path tmp) throws Exception {
        BuildLogService svc = newServiceWithDir(tmp, 1L);
        assertThatThrownBy(() -> svc.read(1L, -5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("offset");
    }
}
