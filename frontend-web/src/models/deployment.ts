export interface Deployment {
  id: number;
  releaseId: number;
  artifactId: number;
  targetEnvId: number;
  result: string;
  message?: string;
  startedAt: string;
  finishedAt?: string;
}