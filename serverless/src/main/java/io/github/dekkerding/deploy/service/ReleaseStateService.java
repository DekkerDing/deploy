package io.github.dekkerding.deploy.service;

import io.github.dekkerding.deploy.domain.IllegalStateTransitionException;
import io.github.dekkerding.deploy.domain.ReleaseState;
import io.github.dekkerding.deploy.domain.entity.ReleaseEntity;
import io.github.dekkerding.deploy.domain.entity.ReleaseEventEntity;
import io.github.dekkerding.deploy.domain.mapper.ReleaseEventMapper;
import io.github.dekkerding.deploy.domain.mapper.ReleaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 发布单状态转移的持久化门面：校验合法性 → 更新 release 行 → 追加 release_event。
 * 状态图见 {@link ReleaseState}（design D4）。
 */
@Service
@RequiredArgsConstructor
public class ReleaseStateService {

    private final ReleaseMapper releaseMapper;
    private final ReleaseEventMapper releaseEventMapper;

    @Transactional
    public ReleaseEntity transition(Long releaseId, ReleaseState target, String message) {
        return transition(releaseId, target, message, null);
    }

    /** 失败转移时附带 fail_reason 落库（release.fail_reason 列）。 */
    @Transactional
    public ReleaseEntity transition(Long releaseId, ReleaseState target, String message, String failReason) {
        ReleaseEntity release = releaseMapper.selectById(releaseId);
        if (release == null) {
            throw new IllegalArgumentException("发布单不存在: id=" + releaseId);
        }
        ReleaseState from = ReleaseState.valueOf(release.getState());
        ReleaseState next = from.transitionTo(target); // 非法流转抛 IllegalStateTransitionException

        release.setState(next.name());
        if (failReason != null) {
            release.setFailReason(failReason);
        }
        release.setUpdatedAt(LocalDateTime.now());
        releaseMapper.updateById(release);

        ReleaseEventEntity event = new ReleaseEventEntity();
        event.setReleaseId(releaseId);
        event.setFromState(from.name());
        event.setToState(next.name());
        event.setMessage(message);
        event.setCreatedAt(LocalDateTime.now());
        releaseEventMapper.insert(event);

        return release;
    }

    /** 查询某发布单是否处于指定状态（供 API 层做前置校验，不改状态）。 */
    public boolean isInState(Long releaseId, ReleaseState expected) {
        ReleaseEntity release = releaseMapper.selectById(releaseId);
        return release != null && ReleaseState.valueOf(release.getState()) == expected;
    }

    /**
     * 不改状态的失败留痕：release.fail_reason 落库 + fromState=toState 的同状态事件。
     * 用于状态机无 FAILED 出边的场景（如 DEPLOYED 下回滚失败须保持 DEPLOYED 以便重试，
     * 直接 transition(FAILED) 会抛 IllegalStateTransitionException 掩盖真实原因）。
     */
    @Transactional
    public ReleaseEntity noteFailure(Long releaseId, String message, String failReason) {
        ReleaseEntity release = releaseMapper.selectById(releaseId);
        if (release == null) {
            throw new IllegalArgumentException("发布单不存在: id=" + releaseId);
        }
        if (failReason != null) {
            release.setFailReason(failReason);
        }
        release.setUpdatedAt(LocalDateTime.now());
        releaseMapper.updateById(release);

        ReleaseEventEntity event = new ReleaseEventEntity();
        event.setReleaseId(releaseId);
        event.setFromState(release.getState());
        event.setToState(release.getState());
        event.setMessage(message);
        event.setCreatedAt(LocalDateTime.now());
        releaseEventMapper.insert(event);
        return release;
    }
}
