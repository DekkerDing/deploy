package io.github.dekkerding.deploy.auth;

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
 * 任务 2.1 验收（specs/console-auth 兼容模式）：
 * 平台令牌为空时全部 API 直接放行（与 MVP curl 用法一致），登录端点明确提示未启用。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-disabled-test;DB_CLOSE_DELAY=-1",
        "deploy.storage-dir=target/auth-test-storage"
})
class AuthDisabledCompatTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 未配置令牌时全部API直接放行() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/releases/1/build-log?offset=0"))
                .andExpect(status().isBadRequest()); // 到达业务端点（发布单不存在被拒），未被认证拦截
    }

    @Test
    void 兼容模式登录端点明确报未启用() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"token\":\"anything\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("未启用认证");
    }
}
