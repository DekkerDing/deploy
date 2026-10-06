package io.github.dekkerding.deploy.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.dekkerding.deploy.domain.ReleaseState.BUILDING;
import static io.github.dekkerding.deploy.domain.ReleaseState.BUILT;
import static io.github.dekkerding.deploy.domain.ReleaseState.CREATED;
import static io.github.dekkerding.deploy.domain.ReleaseState.DEPLOYED;
import static io.github.dekkerding.deploy.domain.ReleaseState.DEPLOYING;
import static io.github.dekkerding.deploy.domain.ReleaseState.FAILED;
import static io.github.dekkerding.deploy.domain.ReleaseState.ROLLED_BACK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 状态机单测：全部合法流转 + 典型非法流转（specs/release-management 场景对照）。 */
class ReleaseStateTest {

    @Test
    void 合法主路径_创建到部署完成() {
        List<ReleaseState> path = new ArrayList<>();
        ReleaseState s = CREATED;
        for (ReleaseState next : new ReleaseState[]{BUILDING, BUILT, DEPLOYING, DEPLOYED}) {
            s = s.transitionTo(next);
            path.add(s);
        }
        assertThat(path).containsExactly(BUILDING, BUILT, DEPLOYING, DEPLOYED);
    }

    @Test
    void 合法失败路径_构建中失败() {
        assertThat(BUILDING.transitionTo(FAILED)).isEqualTo(FAILED);
    }

    @Test
    void 合法失败路径_交付中失败() {
        assertThat(DEPLOYING.transitionTo(FAILED)).isEqualTo(FAILED);
    }

    @Test
    void 合法回滚_已部署转回滚() {
        assertThat(DEPLOYED.transitionTo(ROLLED_BACK)).isEqualTo(ROLLED_BACK);
    }

    @Test
    void 非法_未构建直接交付() {
        // spec 场景：CREATED 发起交付必须被拒
        assertThatThrownBy(() -> CREATED.transitionTo(DEPLOYING))
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("CREATED");
        assertThatThrownBy(() -> CREATED.transitionTo(DEPLOYED))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void 非法_跳过构建进入已构建() {
        assertThatThrownBy(() -> CREATED.transitionTo(BUILT))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void 非法_已构建直接标记部署完成() {
        assertThatThrownBy(() -> BUILT.transitionTo(DEPLOYED))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void 非法_终态不能流转() {
        assertThatThrownBy(() -> FAILED.transitionTo(BUILDING))
                .isInstanceOf(IllegalStateTransitionException.class);
        assertThatThrownBy(() -> ROLLED_BACK.transitionTo(DEPLOYING))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void 非法_已部署回退重建设() {
        assertThatThrownBy(() -> DEPLOYED.transitionTo(BUILDING))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void canTransitionTo_不抛异常仅返回布尔() {
        assertThat(CREATED.canTransitionTo(BUILDING)).isTrue();
        assertThat(CREATED.canTransitionTo(DEPLOYING)).isFalse();
        assertThat(FAILED.legalNextStates()).isEmpty();
    }
}
