package io.github.dekkerding.deploy.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存会话（design D3）：单用户过渡实现，平台重启即全部失效；
 * 服务器化前升级为多用户与凭据治理（design D10 安全边界）。
 */
@Component
@RequiredArgsConstructor
public class AuthSessionStore {

    private final AuthProperties properties;
    private final Map<String, Instant> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /**
     * 令牌比对：匹配则建立会话并返回会话标记；不匹配返回 null。
     * 错误响应不回显令牌内容（specs console-auth 登录拒绝）。
     */
    public String login(String token) {
        String expected = properties.getToken();
        if (!properties.enabled()) {
            throw new IllegalArgumentException("平台未启用认证（deploy.auth.token 为空），无需登录");
        }
        if (token == null || !constantTimeEquals(expected, token)) {
            return null;
        }
        byte[] buf = new byte[24];
        random.nextBytes(buf);
        String sessionId = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        sessions.put(sessionId, Instant.now().plus(properties.getSessionTtl()));
        return sessionId;
    }

    /** 会话是否有效（过期即剔除）。 */
    public boolean isValid(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return false;
        }
        Instant expireAt = sessions.get(sessionId);
        if (expireAt == null) {
            return false;
        }
        if (Instant.now().isAfter(expireAt)) {
            sessions.remove(sessionId);
            return false;
        }
        return true;
    }

    public void logout(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    /** 常数时间比对，避免逐字符短路泄露前缀匹配长度。 */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
