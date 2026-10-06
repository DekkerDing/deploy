package io.github.dekkerding.deploy.service;

import io.github.dekkerding.deploy.domain.entity.ArtifactEntity;
import io.github.dekkerding.deploy.domain.entity.TargetEnvEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 制品-目标路由决策（design D6 三级策略）：
 * 1. 精确匹配：制品平台三字段与目标 os/arch/libc 完全一致；
 * 2. PORTABLE 回退：portable=true（纯 JVM 制品）可路由到任意平台（JVM 字节码预检另行执行）；
 * 3. 拒绝：附制品平台清单，指明目标平台与候选差距。
 * 目标 probe_status=UNKNOWN 时一律拒绝（探针未识别，人工确认前不交付）。
 */
@Component
public class RoutingResolver {

    public static class Decision {
        public final boolean matched;
        public final ArtifactEntity artifact;
        public final String reason;
        public final List<String> platformList;

        private Decision(boolean matched, ArtifactEntity artifact, String reason, List<String> platformList) {
            this.matched = matched;
            this.artifact = artifact;
            this.reason = reason;
            this.platformList = platformList;
        }

        static Decision ok(ArtifactEntity a, String reason) {
            return new Decision(true, a, reason, null);
        }

        static Decision reject(String reason, List<String> platformList) {
            return new Decision(false, null, reason, platformList);
        }
    }

    public Decision resolve(List<ArtifactEntity> artifacts, TargetEnvEntity env) {
        if (env.getProbeStatus() != null && "UNKNOWN".equals(env.getProbeStatus())) {
            return Decision.reject("目标环境探针未识别（UNKNOWN），人工确认前阻止交付: " + env.getName(), null);
        }
        if (artifacts == null || artifacts.isEmpty()) {
            return Decision.reject("发布单没有可交付制品", null);
        }

        List<String> platforms = new ArrayList<>();
        ArtifactEntity portable = null;
        for (ArtifactEntity a : artifacts) {
            String desc = describe(a);
            platforms.add(desc);
            if (Boolean.TRUE.equals(a.getPortable())) {
                if (portable == null) {
                    portable = a;
                }
                continue;
            }
            if (exactMatch(a, env)) {
                return Decision.ok(a, "精确匹配: " + desc);
            }
        }
        if (portable != null) {
            return Decision.ok(portable, "PORTABLE 回退: " + describe(portable) + " → 可路由到 "
                    + env.getOs() + "/" + env.getArch());
        }
        return Decision.reject("无匹配制品: 目标平台 " + env.getOs() + "/" + env.getArch()
                        + "/" + (env.getLibc() == null ? "-" : env.getLibc())
                        + "，制品平台清单 " + platforms,
                platforms);
    }

    private static boolean exactMatch(ArtifactEntity a, TargetEnvEntity env) {
        return eq(a.getPlatformOs(), env.getOs())
                && eq(a.getPlatformArch(), env.getArch())
                && (a.getPlatformLibc() == null || eq(a.getPlatformLibc(), env.getLibc()));
    }

    private static boolean eq(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }

    private static String describe(ArtifactEntity a) {
        if (Boolean.TRUE.equals(a.getPortable())) {
            return a.getFileName() + "[PORTABLE]";
        }
        return a.getFileName() + "[" + a.getPlatformOs() + "/" + a.getPlatformArch()
                + "/" + (a.getPlatformLibc() == null ? "-" : a.getPlatformLibc()) + "]";
    }
}
