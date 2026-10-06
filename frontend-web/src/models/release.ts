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