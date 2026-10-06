package io.github.dekkerding.deploy.api;

import io.github.dekkerding.deploy.domain.IllegalStateTransitionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** 统一错误响应：领域异常 → 明确 HTTP 状态 + message（specs 要求错误信息可读）。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<Map<String, Object>> illegalTransition(IllegalStateTransitionException e) {
        return body(HttpStatus.CONFLICT, e.getMessage(), "ILLEGAL_STATE_TRANSITION");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, Object>> duplicate(DuplicateKeyException e) {
        // 服务层主动抛出的冲突带可读消息（"项目名称已存在: xxx"）；底层数据库约束冲突则给通用提示
        String msg = e.getMessage();
        if (msg == null || !msg.contains("已存在")) {
            msg = "名称或版本已存在（唯一约束冲突）";
        }
        return body(HttpStatus.CONFLICT, msg, "DUPLICATE");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception e) {
        log.error("未处理异常", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误: " + e.getMessage(), "INTERNAL_ERROR");
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, String code) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("message", message);
        return ResponseEntity.status(status).body(m);
    }
}
