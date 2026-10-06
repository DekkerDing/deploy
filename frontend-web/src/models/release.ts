export interface Release {
  id: number;
  projectId: number;
  version: string;
  state: string;
  failReason?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Artifact {
  id: number;
  releaseId: number;
  projectId: number;
  fileName: string;
  storagePath: string;
  sizeBytes: number;
  sha256: string;
  platformOs?: string;
  platformArch?: string;
  platformLibc?: string;
  portable: boolean;
  createdAt: string;
}

export interface ReleaseEvent {
  id: number;
  releaseId: number;
  fromState?: string;
  toState: string;
  message?: string;
  createdAt: string;
}

export interface ReleaseDetail {
  release: Release;
  artifacts: Artifact[];
  /** 后端字段名为 timeline（状态事件时间线），对齐 ReleaseService.ReleaseDetail */
  timeline: ReleaseEvent[];
  deployments?: DeploymentEntity[];
  buildLogUrl?: string;
}

/** detail 中内嵌的部署记录（与 /deployments 端点元素同构，字段为 ISO 字符串） */
interface DeploymentEntity {
  id: number;
  result: string;
  message?: string;
  startedAt: string;
  finishedAt?: string;
}

/** 构建日志增量块（GET /api/releases/{id}/build-log?offset=N）。 */
export interface BuildLogChunk {
  releaseId: number;
  state: string;
  exists: boolean;
  offset: number;
  nextOffset: number;
  size: number;
  truncated: boolean;
  content: string;
}