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
  /** 多实例基准端口：第 i 实例监听 basePort+(i-1)；空=单实例语义 */
  basePort?: number;
  probeStatus: string;
  createdAt: string;
  updatedAt: string;
}