export interface TargetEnv {
  id: number;
  name: string;
  os: string;
  arch: string;
  libc?: string;
  runtime: string;
  reach: string;
  host?: string;
  port?: number;
  username?: string;
  credential?: string;
  jvmVersion?: number;
  healthCheckPort?: number;
  probeStatus: string;
  createdAt: string;
  updatedAt: string;
}