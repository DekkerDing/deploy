package io.github.dekkerding.deploy.api;

import io.github.dekkerding.deploy.domain.entity.ArtifactEntity;
import io.github.dekkerding.deploy.service.ArtifactService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 制品 API（specs/artifact-management: 列表与下载）。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ArtifactController {

    private final ArtifactService artifactService;

    /** 发布单制品列表。 */
    @GetMapping("/releases/{releaseId}/artifacts")
    public List<ArtifactEntity> listByRelease(@PathVariable Long releaseId) {
        return artifactService.listByRelease(releaseId);
    }

    /** 制品下载：流式返回文件，附 sha256 响应头供校验。 */
    @GetMapping("/artifacts/{id}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long id) {
        ArtifactEntity artifact = artifactService.getByIdOrThrow(id);
        Path file = artifactService.resolveStorageFile(artifact);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("制品物理文件缺失: " + artifact.getStoragePath());
        }
        long length;
        try {
            length = Files.size(file);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("制品文件读取失败: " + file, e);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + artifact.getFileName() + "\"")
                .header("X-Artifact-Sha256", artifact.getSha256())
                .contentLength(length)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file));
    }
}
