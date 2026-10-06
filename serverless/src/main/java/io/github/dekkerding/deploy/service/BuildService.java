package io.github.dekkerding.deploy.service;

import io.github.dekkerding.deploy.build.BuildContext;
import io.github.dekkerding.deploy.build.BuildExecutor;
import io.github.dekkerding.deploy.build.BuildExecutorRegistry;
import io.github.dekkerding.deploy.build.BuildResult;
import io.github.dekkerding.deploy.build.BuildType;
import io.github.dekkerding.deploy.domain.ReleaseState;
import io.github.dekkerding.deploy.domain.entity.ProjectEntity;
import io.github.dekkerding.deploy.domain.entity.ReleaseEntity;
import io.github.dekkerding.deploy.domain.mapper.ReleaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 构建编排：触发（CREATED→BUILDING）后异步执行，落终态 BUILT / FAILED。
 * 单工作线程串行执行（并发控制在任务 3.4 形式化）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuildService {

    private final ReleaseMapper releaseMapper;
    private final ReleaseStateService releaseStateService;
    private final ProjectService projectService;
    private final BuildExecutorRegistry executorRegistry;

    @Value("${deploy.log-dir:./logs}")
    private String logDir;

    @Value("${deploy.build.timeout-millis:600000}")
    private long timeoutMillis;

    private final ExecutorService buildWorker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "build-worker");
        t.setDaemon(true);
        return t;
    });

    /** 触发构建：状态先同步流转到 BUILDING 并返回，真实构建在后台线程执行。 */
    public ReleaseEntity trigger(Long releaseId) {
        ReleaseEntity release = releaseStateService.transition(releaseId, ReleaseState.BUILDING, "触发构建");
        buildWorker.submit(() -> runSafely(releaseId));
        return release;
    }

    private void runSafely(Long releaseId) {
        try {
            runBuild(releaseId);
        } catch (Exception e) {
            log.error("构建编排异常 releaseId={}", releaseId, e);
            try {
                releaseStateService.transition(releaseId, ReleaseState.FAILED,
                        "构建编排异常: " + e.getMessage(), e.getMessage());
            } catch (Exception ex) {
                log.error("失败状态落库也失败 releaseId={}", releaseId, ex);
            }
        }
    }

    private void runBuild(Long releaseId) {
        ReleaseEntity release = releaseMapper.selectById(releaseId);
        ProjectEntity project = projectService.getByIdOrThrow(release.getProjectId());
        BuildType type = BuildType.valueOf(project.getBuildType());

        Path logFile = Paths.get(logDir, String.valueOf(releaseId), "build.log");
        BuildContext ctx = BuildContext.builder()
                .releaseId(releaseId)
                .projectId(project.getId())
                .projectName(project.getName())
                .buildType(type)
                .sourcePath(Paths.get(project.getSourcePath()))
                .logFile(logFile)
                .timeoutMillis(timeoutMillis)
                .build();

        BuildExecutor executor = executorRegistry.resolve(type);
        BuildResult result = executor.execute(ctx);

        if (result.isSuccess()) {
            releaseStateService.transition(releaseId, ReleaseState.BUILT,
                    "构建成功: 产物 " + result.getProducedFiles().size() + " 个（"
                            + result.getProducedFiles() + "）");
        } else {
            releaseStateService.transition(releaseId, ReleaseState.FAILED,
                    "构建失败: " + result.getMessage(), result.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        buildWorker.shutdownNow();
    }
}
