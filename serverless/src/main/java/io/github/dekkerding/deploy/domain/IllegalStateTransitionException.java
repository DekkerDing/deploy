package io.github.dekkerding.deploy.domain;

/** 发布单非法状态流转的领域异常（specs/release-management：非法跳转必须被拒绝并返回错误）。 */
public class IllegalStateTransitionException extends RuntimeException {

    private final ReleaseState from;
    private final ReleaseState to;

    public IllegalStateTransitionException(ReleaseState from, ReleaseState to) {
        super(String.format("发布单当前状态 %s 不允许流转到 %s（合法目标: %s）",
                from, to, from.legalNextStates()));
        this.from = from;
        this.to = to;
    }

    public ReleaseState getFrom() {
        return from;
    }

    public ReleaseState getTo() {
        return to;
    }
}
