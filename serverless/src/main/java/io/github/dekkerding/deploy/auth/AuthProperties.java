package io.github.dekkerding.deploy.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 认证配置（design D3 / specs console-auth）。
 * token 为空 = 认证整体关闭（零鉴权兼容模式，保持既有 curl 用法不变）。
 */
@Data
@Component
@ConfigurationProperties("deploy.auth")
public class AuthProperties {

    /** 平台令牌：空 = 兼容模式，全部 API 直接放行 */
    private String token = "";

    /** 登录会话有效期 */
    private Duration sessionTtl = Duration.ofHours(12);

    /** 认证是否启用（token 已配置）。 */
    public boolean enabled() {
        return token != null && !token.trim().isEmpty();
    }
}
