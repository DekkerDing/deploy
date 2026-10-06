package io.github.dekkerding.deploy.delivery;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;

class HealthCheckerTest {

    private HealthChecker checker(int retryTimes, long interval, int timeout) throws Exception {
        HealthChecker h = new HealthChecker();
        set(h, "retryTimes", retryTimes);
        set(h, "intervalMillis", interval);
        set(h, "connectTimeoutMillis", timeout);
        return h;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = HealthChecker.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void 服务在线判定成功() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            HealthChecker h = checker(10, 200, 1000);
            HealthChecker.HealthResult r = h.check("127.0.0.1", port);
            assertThat(r.healthy).isTrue();
            assertThat(r.attempts).isEqualTo(1); // 在线应首探即中
            assertThat(r.message).contains("在线");
        }
    }

    @Test
    void 不可达判定在窗口内重试后失败() throws Exception {
        // 找一个必然空闲的端口：先占后放，TIME_WAIT 短期内无人监听
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        HealthChecker h = checker(3, 150, 300);
        HealthChecker.HealthResult r = h.check("127.0.0.1", port);
        assertThat(r.healthy).isFalse();
        assertThat(r.attempts).isEqualTo(3); // 重试次数用满
        assertThat(r.message).contains("不可达");
    }

    // 注：「窗口内迟启动最终在线」场景在 Windows 单测环境无法确定性构造
    // （保留端口区间静默丢弃、释放-重绑间隙端口被复用导致 bind 静默失败），
    // 该语义由 7.1 全链路中真实 WinSW 服务慢启动（重试窗口内转 DEPLOYED）覆盖。
}
