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
  events: ReleaseEvent[];
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