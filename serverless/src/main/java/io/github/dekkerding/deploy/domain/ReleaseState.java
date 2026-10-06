package io.github.dekkerding.deploy.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * 发布单状态机（specs/release-management）：
 *
 *   CREATED → BUILDING → BUILT → DEPLOYING → DEPLOYED → ROLLED_BACK
 *                │                   │
 *                └──→ FAILED ←──────┘
 *
 * FAILED 与 ROLLED_BACK 为终态（重新发布 = 新建发布单）。
 * 非法转移抛 {@link IllegalStateTransitionException}。
 */
public enum ReleaseState {

    CREATED,
    BUILDING,
    BUILT,
    DEPLOYING,
    DEPLOYED,
    FAILED,
    ROLLED_BACK;

    private static final Map<ReleaseState, EnumSet<ReleaseState>> TRANSITIONS;

    static {
        Map<ReleaseState, EnumSet<ReleaseState>> m = new HashMap<>();
        m.put(CREATED,   EnumSet.of(BUILDING));
        m.put(BUILDING,  EnumSet.of(BUILT, FAILED));
        m.put(BUILT,     EnumSet.of(DEPLOYING));
        m.put(DEPLOYING, EnumSet.of(DEPLOYED, FAILED));
        m.put(DEPLOYED,  EnumSet.of(ROLLED_BACK));
        m.put(FAILED,    EnumSet.noneOf(ReleaseState.class));
        m.put(ROLLED_BACK, EnumSet.noneOf(ReleaseState.class));
        TRANSITIONS = Collections.unmodifiableMap(m);
    }

    /** 是否允许从当前状态转移到目标状态。 */
    public boolean canTransitionTo(ReleaseState target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** 执行转移：合法返回目标状态，非法抛领域异常。 */
    public ReleaseState transitionTo(ReleaseState target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateTransitionException(this, target);
        }
        return target;
    }

    public EnumSet<ReleaseState> legalNextStates() {
        return EnumSet.copyOf(TRANSITIONS.get(this));
    }
}
