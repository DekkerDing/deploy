package io.github.dekkerding.deploy.delivery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 部署后健康检查（任务 6.6）：TCP 端口探活 + 重试窗口。
 * 在线判定：窗口内任意一次 connect 成功即健康；
 * 不可达判定：窗口耗尽全部失败 → 超时失败（交付落 FAILED）。
 */
@Slf4j
@Component
public class HealthChecker {

    @Value("${deploy.health.retry-times:30}")
    private int retryTimes;

    @Value("${deploy.health.interval-millis:2000}")
    private long intervalMillis;

    @Value("${deploy.health.connect-timeout-millis:3000}")
    private int connectTimeoutMillis;

    public static class HealthResult {
        public final boolean healthy;
        public final String message;
        public final int attempts;

        HealthResult(boolean healthy, String message, int attempts) {
            this.healthy = healthy;
            this.message = message;
            this.attempts = attempts;
        }
    }

    /** 探活 host:port，重试窗口内轮询（窗口 = retryTimes × (interval + connectTimeout)）。 */
    public HealthResult check(String host, int port) {
        int attempt = 0;
        IOException last = null;
        while (attempt < retryTimes) {
            attempt++;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
                return new HealthResult(true,
                        "健康检查通过: " + host + ":" + port + " 在线（第 " + attempt + " 次探测）", attempt);
            } catch (IOException e) {
                last = e;
                log.debug("健康探测失败 {}/{} {}:{}: {}", attempt, retryTimes, host, port, e.getMessage());
                if (attempt < retryTimes) {
                    try {
                        Thread.sleep(intervalMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        return new HealthResult(false, "健康检查失败: " + host + ":" + port + " 在 "
                + attempt + " 次探测/" + (retryTimes * intervalMillis / 1000) + "s 窗口后仍不可达 ("
                + (last == null ? "interrupted" : last.getMessage()) + ")", attempt);
    }
}
