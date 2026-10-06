package io.github.dekkerding.deploy.service;

import io.github.dekkerding.deploy.build.ArtifactPlatformDetector;
import io.github.dekkerding.deploy.domain.entity.ArtifactEntity;
import io.github.dekkerding.deploy.domain.mapper.ArtifactMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 制品入库（specs/artifact-management）：
 * 构建产物复制到 storage/{project}/{version}/，登记大小与 sha256 元数据。
 * 平台描述符（PORTABLE/平台绑定）在任务 4.2 接入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArtifactService {

    private final ArtifactMapper artifactMapper;

    @Value("${deploy.storage-dir:./storage}")
    private String storageDir;

    /** 将构建产物入库；返回登记的制品记录。目录型产物（npm dist）暂跳过（后续打包任务处理）。 */
    public List<ArtifactEntity> ingest(Long releaseId, Long projectId, String projectName,
                                       String version, List<Path> producedFiles) {
        List<ArtifactEntity> saved = new ArrayList<>();
        if (producedFiles == null || producedFiles.isEmpty()) {
            return saved;
        }
        Path dir = Paths.get(storageDir, projectName, version);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("制品目录创建失败: " + dir, e);
        }
        for (Path file : producedFiles) {
            if (!Files.isRegularFile(file)) {
                log.info("跳过非文件产物（目录型产物待打包支持）: {}", file);
                continue;
            }
            String name = file.getFileName().toString();
            Path target = dir.resolve(name);
            try {
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new IllegalStateException("制品复制失败: " + file + " -> " + target, e);
            }
            ArtifactEntity a = new ArtifactEntity();
            a.setReleaseId(releaseId);
            a.setProjectId(projectId);
            a.setFileName(name);
            a.setStoragePath(projectName + "/" + version + "/" + name);
            a.setSizeBytes(sizeOf(target));
            a.setSha256(sha256Of(target));
            // 平台描述符（任务 4.2）：纯 JAR/WAR → PORTABLE；原生/未知 → 绑定构建宿主平台
            ArtifactPlatformDetector.Descriptor d = ArtifactPlatformDetector.detect(name);
            a.setPortable(d.portable);
            a.setPlatformOs(d.os);
            a.setPlatformArch(d.arch);
            a.setPlatformLibc(d.libc);
            a.setCreatedAt(LocalDateTime.now());
            artifactMapper.insert(a);
            saved.add(a);
        }
        return saved;
    }

    static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return -1;
        }
    }

    /** 流式计算 sha256（小写十六进制）。 */
    public static String sha256Of(Path file) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取制品计算 sha256 失败: " + file, e);
        }
        StringBuilder sb = new StringBuilder(64);
        for (byte b : md.digest()) {
            sb.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        }
        return sb.toString();
    }

    /** 解析制品的物理存储绝对路径（下载/交付用）。 */
    public Path resolveStorageFile(ArtifactEntity artifact) {
        return Paths.get(storageDir).resolve(artifact.getStoragePath()).toAbsolutePath().normalize();
    }

    /** 发布单制品列表（发布单不存在时报错）。 */
    public List<ArtifactEntity> listByRelease(Long releaseId) {
        return artifactMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ArtifactEntity>()
                .eq("release_id", releaseId).orderByAsc("id"));
    }

    public ArtifactEntity getByIdOrThrow(Long id) {
        ArtifactEntity a = artifactMapper.selectById(id);
        if (a == null) {
            throw new IllegalArgumentException("制品不存在: id=" + id);
        }
        return a;
    }
}
