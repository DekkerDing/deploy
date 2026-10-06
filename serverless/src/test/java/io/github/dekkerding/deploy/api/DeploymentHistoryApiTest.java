package io.github.dekkerding.deploy.api;

import io.github.dekkerding.deploy.domain.entity.DeploymentEntity;
import io.github.dekkerding.deploy.domain.entity.ProjectEntity;
import io.github.dekkerding.deploy.domain.entity.ReleaseEntity;
import io.github.dekkerding.deploy.domain.entity.TargetEnvEntity;
import io.github.dekkerding.deploy.domain.mapper.DeploymentMapper;
import io.github.dekkerding.deploy.domain.mapper.ProjectMapper;
import io.github.dekkerding.deploy.domain.mapper.ReleaseMapper;
import io.github.dekkerding.deploy.domain.mapper.TargetEnvMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 任务 6.8 验收：部署历史查询 API（按发布单 / 按目标环境），返回按时间排序的部署记录。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:history-api-test;DB_CLOSE_DELAY=-1",
        "deploy.storage-dir=target/history-api-test-storage"
})
class DeploymentHistoryApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProjectMapper projectMapper;
    @Autowired
    private ReleaseMapper releaseMapper;
    @Autowired
    private TargetEnvMapper targetEnvMapper;
    @Autowired
    private DeploymentMapper deploymentMapper;

    @Test
    void 按目标环境查询返回时间倒序部署记录() throws Exception {
        Long envId = seedEnv("hist-env-a");
        Long releaseId = seedBuiltRelease("1.0.0");
        insertDeployment(releaseId, envId, "SUCCESS", "第一次");
        Thread.sleep(10); // 保证自增 id 递增有序
        insertDeployment(releaseId, envId, "FAILED", "第二次");
        Thread.sleep(10);
        insertDeployment(releaseId, envId, "ROLLED_BACK", "第三次");

        MvcResult result = mockMvc.perform(get("/api/target-envs/{id}/deployments", envId))
                .andExpect(status().isOk())
                .andReturn();
        // 显式 UTF-8：MockMvc 默认 ISO-8859-1 会吞掉中文
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        // 时间倒序：最新在前（ROLLED_BACK → FAILED → SUCCESS）
        int p1 = body.indexOf("\"message\":\"第一次\"");
        int p2 = body.indexOf("\"message\":\"第二次\"");
        int p3 = body.indexOf("\"message\":\"第三次\"");
        assertThat(p3).isLessThan(p2).isLessThan(p1);
        assertThat(body).contains("\"result\":\"ROLLED_BACK\"").contains("\"result\":\"FAILED\"");
    }

    @Test
    void 按发布单查询只返回该发布单记录() throws Exception {
        Long envId = seedEnv("hist-env-b");
        Long releaseA = seedBuiltRelease("2.0.0");
        Long releaseB = seedBuiltRelease("2.0.1");
        insertDeployment(releaseA, envId, "SUCCESS", "release-a-1");
        insertDeployment(releaseB, envId, "SUCCESS", "release-b-1");
        insertDeployment(releaseA, envId, "ROLLED_BACK", "release-a-2");

        MvcResult result = mockMvc.perform(get("/api/releases/{id}/deployments", releaseA))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("release-a-1").contains("release-a-2");
        assertThat(body).doesNotContain("release-b-1");
    }

    @Test
    void 环境不存在时返回错误() throws Exception {
        mockMvc.perform(get("/api/target-envs/{id}/deployments", 99999L))
                .andExpect(status().is4xxClientError());
    }

    // ---- 数据准备 ----

    private Long seedEnv(String name) {
        TargetEnvEntity env = new TargetEnvEntity();
        env.setName(name + "-" + System.nanoTime());
        env.setOs("linux");
        env.setArch("amd64");
        env.setRuntimeType("NATIVE");
        env.setReach("LOCAL");
        env.setProbeStatus("KNOWN");
        env.setCreatedAt(LocalDateTime.now());
        env.setUpdatedAt(LocalDateTime.now());
        targetEnvMapper.insert(env);
        return env.getId();
    }

    private Long seedBuiltRelease(String version) {
        ProjectEntity p = new ProjectEntity();
        p.setName("hist-proj-" + System.nanoTime());
        p.setBuildType("GRADLE");
        p.setSourcePath("F:/workspace/deploy/sample-apps/hello-jar");
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(p);
        ReleaseEntity r = new ReleaseEntity();
        r.setProjectId(p.getId());
        r.setVersion(version + "-" + System.nanoTime());
        r.setState("BUILT");
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        releaseMapper.insert(r);
        return r.getId();
    }

    private void insertDeployment(Long releaseId, Long envId, String result, String message) {
        DeploymentEntity d = new DeploymentEntity();
        d.setReleaseId(releaseId);
        d.setArtifactId(1L);
        d.setTargetEnvId(envId);
        d.setResult(result);
        d.setMessage(message);
        d.setStartedAt(LocalDateTime.now());
        d.setFinishedAt(LocalDateTime.now());
        deploymentMapper.insert(d);
    }
}
