package io.github.dekkerding.deploy.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:target-env-test;DB_CLOSE_DELAY=-1"
})
class TargetEnvControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private MvcResult register(String body) throws Exception {
        return mockMvc.perform(post("/api/target-envs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    @Test
    void 未列举系统freebsd注册无需改代码() throws Exception {
        register("{\"name\":\"bsd-box-1\",\"os\":\"freebsd\",\"arch\":\"X86_64\",\"libc\":\"\",\""
                + "runtimeType\":\"NATIVE\",\"reach\":\"SSH\",\"host\":\"192.168.1.50\",\"port\":22,"
                + "\"username\":\"root\",\"credential\":\"pw\"}")
                .getResponse();
        mockMvc.perform(get("/api/target-envs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='bsd-box-1')].os").value("freebsd"))
                .andExpect(jsonPath("$[?(@.name=='bsd-box-1')].arch").value("amd64"))
                .andExpect(jsonPath("$[?(@.name=='bsd-box-1')].runtimeType").value("NATIVE"))
                .andExpect(jsonPath("$[?(@.name=='bsd-box-1')].reach").value("SSH"))
                .andExpect(jsonPath("$[?(@.name=='bsd-box-1')].probeStatus").value("KNOWN"));
    }

    @Test
    void 重复名称注册被拒绝() throws Exception {
        String body = "{\"name\":\"dup-env\",\"os\":\"linux\",\"arch\":\"arm64\",\"libc\":\"musl\","
                + "\"runtimeType\":\"DOCKER\",\"reach\":\"SSH\",\"host\":\"10.0.0.2\"}";
        register(body);
        MvcResult second = register(body);
        org.assertj.core.api.Assertions.assertThat(second.getResponse().getStatus()).isEqualTo(409);
        org.assertj.core.api.Assertions.assertThat(second.getResponse().getContentAsString())
                .contains("\"DUPLICATE\"").contains("dup-env");
    }

    @Test
    void SSH通道缺host被拒_JVM载体默认版本8() throws Exception {
        MvcResult missingHost = register("{\"name\":\"no-host\",\"os\":\"linux\",\"arch\":\"amd64\","
                + "\"runtimeType\":\"JVM\",\"reach\":\"SSH\"}");
        org.assertj.core.api.Assertions.assertThat(missingHost.getResponse().getStatus()).isEqualTo(400);

        MvcResult jvm = register("{\"name\":\"jvm-env\",\"os\":\"windows\",\"arch\":\"amd64\","
                + "\"runtimeType\":\"jvm\",\"reach\":\"LOCAL\"}");
        org.assertj.core.api.Assertions.assertThat(jvm.getResponse().getContentAsString())
                .contains("\"jvmVersion\":8");
    }
}
