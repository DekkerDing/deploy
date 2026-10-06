package io.github.dekkerding.deploy.service;

import io.github.dekkerding.deploy.domain.mapper.ReleaseMapper;
import io.github.dekkerding.deploy.domain.entity.ReleaseEntity;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 构建日志增量读取（specs/build-execution: 偏移量增量 API）。
 *
 * offset 语义为**字节**偏移（非字符）：客户端回传 nextOffset 续读；
 * 单次最多返回 {@value #MAX_CHUNK_BYTES} 字节（truncated=true 表示还有更多）。
 * 每次都从磁盘读取，无内存态 —— 平台重启后历史日志天然可读。
 */
@Service
@RequiredArgsConstructor
public class BuildLogService {

    static final int MAX_CHUNK_BYTES = 262144; // 256KB/次

    private final ReleaseMapper releaseMapper;

    @Value("${deploy.log-dir:./logs}")
    private String logDir;

    @Data
    public static class LogChunk {
        private Long releaseId;
        private String state;
        private boolean exists;
        private long offset;      // 本次请求的起始偏移
        private long nextOffset;  // 下次续读偏移（= offset + 本次实际读到的字节数）
        private long size;        // 当前文件总字节大小
        private boolean truncated; // 因单次上限截断
        private String content;   // UTF-8 解码内容（截断处多字节字符以 � 替换）
    }

    public LogChunk read(Long releaseId, long offset) {
        ReleaseEntity release = releaseMapper.selectById(releaseId);
        if (release == null) {
            throw new IllegalArgumentException("发布单不存在: id=" + releaseId);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset 不能为负: " + offset);
        }

        LogChunk chunk = new LogChunk();
        chunk.setReleaseId(releaseId);
        chunk.setState(release.getState());
        chunk.setOffset(offset);

        Path file = Paths.get(logDir, String.valueOf(releaseId), "build.log");
        if (!java.nio.file.Files.exists(file)) {
            chunk.setExists(false);
            chunk.setNextOffset(0);
            chunk.setSize(0);
            chunk.setContent("");
            return chunk;
        }

        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            long size = ch.size();
            chunk.setExists(true);
            chunk.setSize(size);
            if (offset >= size) {
                chunk.setNextOffset(size);
                chunk.setContent("");
                return chunk;
            }
            ch.position(offset);
            int toRead = (int) Math.min(MAX_CHUNK_BYTES, size - offset);
            ByteBuffer buf = ByteBuffer.allocate(toRead);
            int read = 0;
            while (read < toRead) {
                int n = ch.read(buf);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            buf.flip();
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
            chunk.setContent(decoder.decode(buf).toString());
            chunk.setNextOffset(offset + read);
            chunk.setTruncated(offset + read < size);
            return chunk;
        } catch (IOException e) {
            throw new IllegalStateException("构建日志读取失败: " + file, e);
        }
    }
}
