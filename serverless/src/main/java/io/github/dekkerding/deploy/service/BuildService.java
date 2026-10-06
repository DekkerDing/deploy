package io.github.dekkerding.deploy.service;

import io.github.dekkerding.deploy.build.BuildContext;
import io.github.dekkerding.deploy.build.BuildExecutor;
import io.github.dekkerding.deploy.build.BuildExecutorRegistry;
import io.github.dekkerding.deploy.build.BuildResult;
import io.github.dekkerding.deploy.build.BuildType;
import io.github.dekkerding.deploy.domain.ReleaseState;
import io.github.dekkerding.deploy.domain.entity.ArtifactEntity;
import io.github.dekkerding.deploy.domain.entity.ProjectEntity;
import io.github.dekkerding.deploy.domain.entity.ReleaseEntity;
import io.github.dekkerding.deploy.domain.mapper.ReleaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

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
    private final ArtifactService artifactService;

    @Value("${deploy.log-dir:./logs}")
    private String logDir;

    @Value("${deploy.build.timeout-millis:600000}")
    private long timeoutMillis;

    @Value("${deploy.build.concurrency:2}")
    private int concurrency;

    @Value("${deploy.build.queue-capacity:50}")
    private int queueCapacity;

    /**
     * 固定线程池 + 有界队列（任务 3.4）：
     * 并发上限内立即执行，超上限进队列等待（不失败）；队列满时触发线程阻塞等待空位
     * （caller-blocks 语义），保证任务不会被拒绝丢弃。
     * 池参数来自配置，故在 @PostConstruct 中构造（@Value 晚于字段初始化注入）。
     */
    private ThreadPoolExecutor buildWorker;

    @PostConstruct
    void initPool() {
        int size = Math.max(1, concurrency);
        buildWorker = new ThreadPoolExecutor(size, size, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(1, queueCapacity)),
                r -> {
                    Thread t = new Thread(r, "build-worker");
                    t.setDaemon(true);
                    return t;
                }) {

            @Override
            public void execute(Runnable command) {
                // 队列满时在触发线程上阻塞重试，而非抛 RejectedExecutionException（排队不失败）
                while (true) {
                    try {
                        super.execute(command);
                        return;
                    } catch (RejectedExecutionException e) {
                        LockSupport.parkNanos(100_000_000L); // 100ms 后重试
                    }
                }
            }
        };
    }

    /** 触发构建：状态先同步流转到 BUILDING 并返回，真实构建在线程池排队/执行。 */
    public ReleaseEntity trigger(Long releaseId) {
        ReleaseEntity release = releaseStateService.transition(releaseId, ReleaseState.BUILDING, "触发构建");
        buildWorker.execute(() -> runSafely(releaseId));
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
            // 制品入库（任务 4.1）：入库成功才落 BUILT，入库异常走 FAILED
            List<ArtifactEntity> artifacts = artifactService.ingest(
                    releaseId, project.getId(), project.getName(), release.getVersion(),
                    result.getProducedFiles());
            releaseStateService.transition(releaseId, ReleaseState.BUILT,
                    "构建成功: 制品入库 " + artifacts.size() + " 个（"
                            + result.getProducedFiles() + "）");
        } else {
            releaseStateService.transition(releaseId, ReleaseState.FAILED,
                    "构建失败: " + result.getMessage(), result.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        if (buildWorker != null) {
            buildWorker.shutdownNow();
        }
    }
}
