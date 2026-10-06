package io.github.dekkerding.deploy.api;

import io.github.dekkerding.deploy.domain.entity.ArtifactEntity;
import io.github.dekkerding.deploy.domain.entity.ProjectEntity;
import io.github.dekkerding.deploy.domain.entity.ReleaseEntity;
import io.github.dekkerding.deploy.domain.mapper.ArtifactMapper;
import io.github.dekkerding.deploy.domain.mapper.ProjectMapper;
import io.github.dekkerding.deploy.domain.mapper.ReleaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 制品列表与下载 API 集成测试（H2 内存库 + build 下的测试存储目录）。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:artifact-api-test;DB_CLOSE_DELAY=-1",
        "deploy.storage-dir=target/artifact-api-storage"
})
class ArtifactControllerTest {

    private static final Path STORAGE = Paths.get("target", "artifact-api-storage");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private ReleaseMapper releaseMapper;

    @Autowired
    private ArtifactMapper artifactMapper;

    private Long releaseId;
    private Long artifactId;
    private String content = "artifact-download-content-3.1.0";
    private String sha256;

    @BeforeEach
    void setUp() throws Exception {
        ProjectEntity p = new ProjectEntity();
        p.setName("artifact-api-test-" + System.nanoTime());
        p.setBuildType("GRADLE");
        p.setSourcePath(STORAGE.toString());
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(p);

        ReleaseEntity r = new ReleaseEntity();
        r.setProjectId(p.getId());
        r.setVersion("1.0.0");
        r.setState("BUILT");
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        releaseMapper.insert(r);
        releaseId = r.getId();

        // 物理制品文件写入测试存储目录
        Path target = STORAGE.resolve(p.getName()).resolve("1.0.0");
        Files.createDirectories(target);
        Path jar = target.resolve("app.jar");
        Files.write(jar, content.getBytes(StandardCharsets.UTF_8));
        sha256 = io.github.dekkerding.deploy.service.ArtifactService.sha256Of(jar);

        ArtifactEntity a = new ArtifactEntity();
        a.setReleaseId(releaseId);
        a.setProjectId(p.getId());
        a.setFileName("app.jar");
        a.setStoragePath(p.getName() + "/1.0.0/app.jar");
        a.setSizeBytes((long) content.length());
        a.setSha256(sha256);
        a.setPortable(Boolean.TRUE);
        a.setCreatedAt(LocalDateTime.now());
        artifactMapper.insert(a);
        artifactId = a.getId();
    }

    @Test
    void 制品列表按发布单返回() throws Exception {
        mockMvc.perform(get("/api/releases/{id}/artifacts", releaseId))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].fileName")
                        .value("app.jar"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].sha256")
                        .value(sha256));
    }

    @Test
    void 下载内容与入库sha256一致() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/artifacts/{id}/download", artifactId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Artifact-Sha256", sha256))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("app.jar")))
                .andReturn();
        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo(content);
        // 下载字节流的 sha256 与入库记录一致
        Path redownloaded = Paths.get("target", "redownload.jar");
        Files.write(redownloaded, body);
        assertThat(io.github.dekkerding.deploy.service.ArtifactService.sha256Of(redownloaded)).isEqualTo(sha256);
    }

    @Test
    void 不存在的制品返回400() throws Exception {
        mockMvc.perform(get("/api/artifacts/{id}/download", 99999L))
                .andExpect(status().isBadRequest());
    }
}
