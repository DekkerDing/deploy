package io.github.dekkerding.deploy.auth;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 登录端点（specs console-auth 令牌登录）。 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthSessionStore sessionStore;
    private final AuthProperties properties;

    @Data
    public static class LoginRequest {
        private String token;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        String sessionId = sessionStore.login(req.getToken());
        if (sessionId == null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", "UNAUTHORIZED");
            body.put("message", "令牌不匹配");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", sessionId);
        body.put("expiresInSeconds", properties.getSessionTtl().getSeconds());
        return ResponseEntity.ok(body);
    }
}
