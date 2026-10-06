import { create } from 'zustand';
import { apiGet, apiPost } from '../core/api';
import { TargetEnv } from '../models/target-env';
import { Deployment } from '../models/deployment';

/** 实例矩阵视图（后端 InstanceView：编号/端口/版本/运行与健康状态） */
export interface InstanceView {
  id: number;
  seq: number;
  port: number;
  releaseId: number;
  version: string | null;
  status: string;
  healthy: boolean | null;
  healthMessage: string | null;
}

interface TargetEnvState {
  envs: TargetEnv[];
  deployments: Deployment[];
  /** 每环境最近一次部署（时间倒序取首个），null=从未部署——健康灯数据源 */
  envHealth: Record<number, Deployment | null>;
  /** 实例矩阵（specs/instance-scaling），key=targetEnvId */
  instances: Record<number, InstanceView[]>;
  loading: boolean;
  error: string | null;

  loadEnvs: () => Promise<void>;
  loadDeployments: (envId: number) => Promise<void>;
  loadEnvHealth: () => Promise<void>;
  loadInstances: (envId: number) => Promise<void>;
  scaleTo: (envId: number, count: number) => Promise<string | null>;
  register: (data: {
    name: string;
    os: string;
    arch: string;
    libc?: string;
    runtimeType: string;
    reach: string;
    host?: string;
    port?: number;
    username?: string;
    credential?: string;
    jvmVersion?: number;
    healthCheckPort?: number;
    basePort?: number;
  }) => Promise<TargetEnv | null>;
}

export const useTargetEnvStore = create<TargetEnvState>((set, get) => ({
  envs: [],
  deployments: [],
  envHealth: {},
  instances: {},
  loading: false,
  error: null,

  loadEnvs: async () => {
    set({ loading: true, error: null });
    try {
      const data = await apiGet<TargetEnv[]>('/api/target-envs');
      set({ envs: data, loading: false });
    } catch (e) {
      set({ error: (e as Error).message, loading: false });
    }
  },

  loadEnvHealth: async () => {
    const entries = await Promise.all(
      get().envs.map(async (e) => {
        try {
          const ds = await apiGet<Deployment[]>(`/api/target-envs/${e.id}/deployments`);
          return [e.id, ds[0] ?? null] as const;
        } catch {
          return [e.id, null] as const;
        }
      })
    );
    set({ envHealth: Object.fromEntries(entries) });
  },

  loadDeployments: async (envId) => {
    try {
      const data = await apiGet<Deployment[]>(`/api/target-envs/${envId}/deployments`);
      set({ deployments: data });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  loadInstances: async (envId) => {
    try {
      const data = await apiGet<InstanceView[]>(`/api/target-envs/${envId}/instances`);
      set((s) => ({ instances: { ...s.instances, [envId]: data } }));
    } catch {
      // 环境无实例或查询失败时置空，矩阵呈现"暂无实例"
      set((s) => ({ instances: { ...s.instances, [envId]: [] } }));
    }
  },

  scaleTo: async (envId, count) => {
    set({ error: null });
    try {
      const res = await apiPost<{ message: string }>(
        `/api/target-envs/${envId}/scale?count=${count}`
      );
      return res.message;
    } catch (e) {
      set({ error: (e as Error).message });
      return null;
    }
  },

  register: async (body) => {
    set({ error: null });
    try {
      const env = await apiPost<TargetEnv>('/api/target-envs', body);
      set((s) => ({ envs: [...s.envs, env] }));
      return env;
    } catch (e) {
      set({ error: (e as Error).message });
      return null;
    }
  },
}));