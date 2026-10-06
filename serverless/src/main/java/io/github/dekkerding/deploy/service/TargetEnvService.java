package io.github.dekkerding.deploy.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.github.dekkerding.deploy.domain.entity.TargetEnvEntity;
import io.github.dekkerding.deploy.domain.mapper.TargetEnvMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 目标环境注册/查询（specs/delivery-routing）。
 * 五维为开放取值：未知系统（如 freebsd）零代码接入，路由按值匹配而非枚举穷举。
 */
@Service
@RequiredArgsConstructor
public class TargetEnvService {

    private final TargetEnvMapper targetEnvMapper;

    public TargetEnvEntity register(TargetEnvEntity env) {
        normalize(env);
        validate(env);
        Long exists = targetEnvMapper.selectCount(new QueryWrapper<TargetEnvEntity>()
                .eq("name", env.getName()));
        if (exists != null && exists > 0) {
            throw new DuplicateKeyException("目标环境已存在: " + env.getName());
        }
        if (env.getProbeStatus() == null) {
            env.setProbeStatus("KNOWN");
        }
        env.setCreatedAt(LocalDateTime.now());
        env.setUpdatedAt(LocalDateTime.now());
        targetEnvMapper.insert(env);
        return env;
    }

    public List<TargetEnvEntity> list() {
        return targetEnvMapper.selectList(new QueryWrapper<TargetEnvEntity>().orderByAsc("id"));
    }

    public TargetEnvEntity getByIdOrThrow(Long id) {
        TargetEnvEntity env = targetEnvMapper.selectById(id);
        if (env == null) {
            throw new IllegalArgumentException("目标环境不存在: id=" + id);
        }
        return env;
    }

    /** 维度值归一化：小写 + 去空白（amd64/AMD64/x86_64 归一到 amd64? 否——统一小写，别名在路由处理）。 */
    private void normalize(TargetEnvEntity env) {
        if (env.getName() != null) {
            env.setName(env.getName().trim());
        }
        env.setOs(lower(env.getOs()));
        env.setArch(normalizeArch(env.getArch()));
        env.setLibc(lower(env.getLibc()));
        env.setRuntimeType(upper(env.getRuntimeType()));
        env.setReach(upper(env.getReach()));
    }

    private void validate(TargetEnvEntity env) {
        require(env.getName(), "name");
        require(env.getOs(), "os");
        require(env.getArch(), "arch");
        require(env.getRuntimeType(), "runtimeType");
        require(env.getReach(), "reach");
        if ("SSH".equals(env.getReach())) {
            require(env.getHost(), "host（SSH 通道必填）");
            if (env.getPort() == null) {
                env.setPort(22);
            }
        }
        if ("JVM".equals(env.getRuntimeType()) && env.getJvmVersion() == null) {
            // 允许为空但路由 JAR 时会因无法预检而拒绝；注册时给默认 8（本平台工程基座）
            env.setJvmVersion(8);
        }
        if (env.getHealthCheckPort() != null
                && (env.getHealthCheckPort() < 1 || env.getHealthCheckPort() > 65535)) {
            throw new IllegalArgumentException("healthCheckPort 必须在 1-65535 之间: " + env.getHealthCheckPort());
        }
    }

    private static String normalizeArch(String rawArch) {
        if (rawArch == null) {
            return null;
        }
        String arch = rawArch.trim().toLowerCase(java.util.Locale.ROOT);
        if (arch.equals("x86_64") || arch.equals("amd64_intel")) {
            return "amd64";
        }
        if (arch.equals("aarch64")) {
            return "arm64";
        }
        return arch;
    }

    private static String require(String v, String field) {
        if (!StringUtils.hasText(v)) {
            throw new IllegalArgumentException("目标环境字段缺失: " + field);
        }
        return v;
    }

    private static String lower(String v) {
        return v == null ? null : v.trim().toLowerCase(Locale.ROOT);
    }

    private static String upper(String v) {
        return v == null ? null : v.trim().toUpperCase(Locale.ROOT);
    }
}
