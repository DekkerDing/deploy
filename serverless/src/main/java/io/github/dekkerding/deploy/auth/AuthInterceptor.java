package io.github.dekkerding.deploy.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * API 访问控制（specs/console-auth）：仅对 /api/** 挂载；
 * 登录端点放行；静态控制台资源、SPA fallback、/h2-console 不在 /api 前缀下不经本拦截器（白名单语义）。
 *
 * 会话标记取 X-Auth-Session 头，或 ?session= 查询参数
 * （后者供 &lt;a href&gt; 直链下载、APP 外部浏览器打开等无法自定义头的场景）。
 * 认证关闭（token 为空）时全部放行（兼容模式）。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_HEADER = "X-Auth-Session";
    public static final String SESSION_QUERY = "session";

    private final AuthProperties properties;
    private final AuthSessionStore sessionStore;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        if (!properties.enabled()) {
            return true;
        }
        if ("/api/auth/login".equals(request.getRequestURI())) {
            return true;
        }
        String sessionId = request.getHeader(SESSION_HEADER);
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = request.getParameter(SESSION_QUERY);
        }
        if (sessionStore.isValid(sessionId)) {
            return true;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter writer = response.getWriter();
        writer.write(unauthorizedBody());
        writer.flush();
        return false;
    }

    private static String unauthorizedBody() {
        // 手拼固定文案；响应体不含业务数据
        return "{\"code\":\"UNAUTHORIZED\",\"message\":\"未认证或会话已过期，请重新登录\"}";
    }
}
