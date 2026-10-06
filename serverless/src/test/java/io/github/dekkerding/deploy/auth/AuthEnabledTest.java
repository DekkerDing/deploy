package io.github.dekkerding.deploy.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 任务 2.1 验收（specs/console-auth 启用模式）：
 * 无会话 401 / 错误令牌拒绝不回显 / 正确令牌登录后放行 / ?session= 直链场景 / 白名单不经校验。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-enabled-test;DB_CLOSE_DELAY=-1",
        "deploy.storage-dir=target/auth-test-storage",
        "deploy.auth.token=e2e-token-123"
})
class AuthEnabledTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthSessionStore sessionStore;

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void 无会话访问业务API返回401且无业务数据() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("未认证").contains("UNAUTHORIZED");
        // 响应体不含业务数据（不是项目列表结构）
        assertThat(body).doesNotContain("records").doesNotContain("projectId");
    }

    @Test
    void 错误令牌登录被拒且不回显令牌() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"token\":\"wrong-token\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("令牌不匹配");
        // 不暴露提交的令牌内容与平台令牌
        assertThat(body).doesNotContain("wrong-token").doesNotContain("e2e-token-123");
    }

    @Test
    void 正确令牌登录后携带会话标记放行() throws Exception {
        String sessionId = login();
        assertThat(sessionId).isNotBlank();

        mockMvc.perform(get("/api/projects").header(AuthInterceptor.SESSION_HEADER, sessionId))
                .andExpect(status().isOk());
    }

    @Test
    void 查询参数会话同样放行() throws Exception {
        // <a href> 直链下载、APP 外部浏览器打开等无法自定义头的场景
        String sessionId = login();
        mockMvc.perform(get("/api/projects").param(AuthInterceptor.SESSION_QUERY, sessionId))
                .andExpect(status().isOk());
    }

    @Test
    void 无效会话被拒() throws Exception {
        mockMvc.perform(get("/api/projects").header(AuthInterceptor.SESSION_HEADER, "forged-session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 白名单路径不经令牌校验() throws Exception {
        // 静态控制台资源与 /h2-console 不在 /api 前缀下：允许 404/302，但不允许 401
        int staticStatus = mockMvc.perform(get("/")).andReturn().getResponse().getStatus();
        assertThat(staticStatus).isNotEqualTo(401);
        int h2Status = mockMvc.perform(get("/h2-console")).andReturn().getResponse().getStatus();
        assertThat(h2Status).isNotEqualTo(401);
        // 登录端点自身放行（上一用例已覆盖成功路径，这里验证无需会话即可到达）
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"token\":\"x\"}"))
                .andExpect(status().isUnauthorized()); // 到达端点后被拒（非拦截器 401 也无妨，语义一致）
    }

    @Test
    void 登出后会话失效() throws Exception {
        String sessionId = login();
        sessionStore.logout(sessionId);
        mockMvc.perform(get("/api/projects").header(AuthInterceptor.SESSION_HEADER, sessionId))
                .andExpect(status().isUnauthorized());
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"token\":\"e2e-token-123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = om.readTree(result.getResponse().getContentAsString());
        return node.get("sessionId").asText();
    }
}
