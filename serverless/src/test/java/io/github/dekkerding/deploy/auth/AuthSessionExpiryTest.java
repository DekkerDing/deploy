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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 任务 2.1 验收：会话 TTL 到期后拒绝（specs console-auth 会话有效期）。 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-expiry-test;DB_CLOSE_DELAY=-1",
        "deploy.storage-dir=target/auth-test-storage",
        "deploy.auth.token=ttl-token",
        "deploy.auth.session-ttl=200ms"
})
class AuthSessionExpiryTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void 会话过期后拒绝() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"token\":\"ttl-token\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = om.readTree(login.getResponse().getContentAsString());
        String sessionId = node.get("sessionId").asText();

        mockMvc.perform(get("/api/projects").header(AuthInterceptor.SESSION_HEADER, sessionId))
                .andExpect(status().isOk());

        Thread.sleep(400); // 越过 200ms TTL

        mockMvc.perform(get("/api/projects").header(AuthInterceptor.SESSION_HEADER, sessionId))
                .andExpect(status().isUnauthorized());
    }
}
