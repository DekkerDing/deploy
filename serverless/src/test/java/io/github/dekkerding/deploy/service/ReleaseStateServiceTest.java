package io.github.dekkerding.deploy.service;

import io.github.dekkerding.deploy.domain.IllegalStateTransitionException;
import io.github.dekkerding.deploy.domain.ReleaseState;
import io.github.dekkerding.deploy.domain.entity.ProjectEntity;
import io.github.dekkerding.deploy.domain.entity.ReleaseEntity;
import io.github.dekkerding.deploy.domain.entity.ReleaseEventEntity;
import io.github.dekkerding.deploy.domain.mapper.ProjectMapper;
import io.github.dekkerding.deploy.domain.mapper.ReleaseEventMapper;
import io.github.dekkerding.deploy.domain.mapper.ReleaseMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 集成测试（H2 内存库 + 真实 schema.sql）：状态转移落库 + release_event 追加 + 非法流转拒绝。 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:statemachine-test;DB_CLOSE_DELAY=-1")
class ReleaseStateServiceTest {

    @Autowired
    private ReleaseStateService releaseStateService;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private ReleaseMapper releaseMapper;

    @Autowired
    private ReleaseEventMapper releaseEventMapper;

    private Long newReleaseInState(ReleaseState state) {
        ProjectEntity p = new ProjectEntity();
        p.setName("st-test-" + System.nanoTime());
        p.setBuildType("MAVEN");
        p.setSourcePath("./somewhere");
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(p);

        ReleaseEntity r = new ReleaseEntity();
        r.setProjectId(p.getId());
        r.setVersion("1.0.0");
        r.setState(state.name());
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        releaseMapper.insert(r);
        return r.getId();
    }

    @Test
    void 转移成功_状态落库且事件追加() {
        Long id = newReleaseInState(ReleaseState.CREATED);

        ReleaseEntity updated = releaseStateService.transition(id, ReleaseState.BUILDING, "触发构建");

        assertThat(updated.getState()).isEqualTo("BUILDING");

        ReleaseEntity fromDb = releaseMapper.selectById(id);
        assertThat(fromDb.getState()).isEqualTo("BUILDING");

        List<ReleaseEventEntity> events = releaseEventMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ReleaseEventEntity>()
                        .eq("release_id", id));
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getFromState()).isEqualTo("CREATED");
        assertThat(events.get(0).getToState()).isEqualTo("BUILDING");
        assertThat(events.get(0).getMessage()).isEqualTo("触发构建");
    }

    @Test
    void 非法转移_拒绝且无副作用() {
        Long id = newReleaseInState(ReleaseState.CREATED);

        assertThatThrownBy(() -> releaseStateService.transition(id, ReleaseState.DEPLOYING, "越级"))
                .isInstanceOf(IllegalStateTransitionException.class);

        assertThat(releaseMapper.selectById(id).getState()).isEqualTo("CREATED");

        List<ReleaseEventEntity> events = releaseEventMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ReleaseEventEntity>()
                        .eq("release_id", id));
        assertThat(events).isEmpty();
    }

    @Test
    void 连续转移_事件按序累积() {
        Long id = newReleaseInState(ReleaseState.CREATED);
        releaseStateService.transition(id, ReleaseState.BUILDING, null);
        releaseStateService.transition(id, ReleaseState.BUILT, "构建成功");
        releaseStateService.transition(id, ReleaseState.DEPLOYING, "发起交付");

        List<ReleaseEventEntity> events = releaseEventMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ReleaseEventEntity>()
                        .eq("release_id", id).orderByAsc("id"));
        assertThat(events).hasSize(3);
        assertThat(events.get(2).getToState()).isEqualTo("DEPLOYING");
    }
}
