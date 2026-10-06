-- deploy-platform 建表脚本（H2 file 模式，幂等；迁 MySQL 时按方言微改）
-- 见 openspec/changes/mvp-build-delivery-platform/design.md D2

CREATE TABLE IF NOT EXISTS project (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128)  NOT NULL,
    build_type  VARCHAR(16)   NOT NULL,          -- MAVEN / GRADLE / NPM
    source_path VARCHAR(512)  NOT NULL,          -- 源码路径（本机目录）
    description VARCHAR(512),
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS release (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id  BIGINT        NOT NULL,
    version     VARCHAR(64)   NOT NULL,
    state       VARCHAR(24)   NOT NULL,          -- ReleaseState 枚举名
    fail_reason VARCHAR(1024),
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_release_project_version UNIQUE (project_id, version)
);

CREATE TABLE IF NOT EXISTS release_event (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    release_id BIGINT       NOT NULL,
    from_state VARCHAR(24),
    to_state   VARCHAR(24)  NOT NULL,
    message    VARCHAR(1024),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS artifact (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    release_id    BIGINT       NOT NULL,
    project_id    BIGINT       NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    storage_path  VARCHAR(1024) NOT NULL,        -- 相对 storage 根的路径
    size_bytes    BIGINT       NOT NULL,
    sha256        CHAR(64)     NOT NULL,
    platform_os   VARCHAR(32),                   -- 空 = PORTABLE
    platform_arch VARCHAR(32),
    platform_libc VARCHAR(32),
    portable      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_artifact_release_file UNIQUE (release_id, file_name)
);

CREATE TABLE IF NOT EXISTS target_env (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,
    os            VARCHAR(32)  NOT NULL,          -- linux / windows / darwin / ...(开放注册)
    arch          VARCHAR(32)  NOT NULL,          -- amd64 / arm64 / armv7 / 386 / ...
    libc          VARCHAR(16),                    -- glibc / musl（仅 linux 有意义）
    runtime_type  VARCHAR(16)  NOT NULL,          -- JVM / DOCKER / K8S / NATIVE
    reach         VARCHAR(16)  NOT NULL,          -- SSH / WINRM / LOCAL
    host          VARCHAR(128),
    port          INT,
    username      VARCHAR(64),
    credential    VARCHAR(1024),                  -- MVP: 密码/私钥明文（design D10 已声明边界）
    jvm_version   INT,                            -- runtime_type=JVM 时的目标大版本
    health_check_port INT,                        -- 部署后 TCP 探活端口（空=跳过健康检查）
    probe_status  VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN', -- KNOWN / UNKNOWN
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_target_env_name UNIQUE (name)
);

-- 已有库补列（H2 幂等；任务 6.7 健康检查端口）
ALTER TABLE target_env ADD COLUMN IF NOT EXISTS health_check_port INT;

CREATE TABLE IF NOT EXISTS deployment (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    release_id    BIGINT      NOT NULL,
    artifact_id   BIGINT      NOT NULL,
    target_env_id BIGINT      NOT NULL,
    result        VARCHAR(16) NOT NULL,           -- SUCCESS / FAILED / ROLLED_BACK
    message       VARCHAR(1024),
    started_at    TIMESTAMP   NOT NULL,
    finished_at   TIMESTAMP
);
