import { create } from 'zustand';
import { apiGet, apiPost } from '../core/api';
import { TargetEnv } from '../models/target-env';
import { Deployment } from '../models/deployment';

interface TargetEnvState {
  envs: TargetEnv[];
  deployments: Deployment[];
  /** 每环境最近一次部署（时间倒序取首个），null=从未部署——健康灯数据源 */
  envHealth: Record<number, Deployment | null>;
  loading: boolean;
  error: string | null;

  loadEnvs: () => Promise<void>;
  loadDeployments: (envId: number) => Promise<void>;
  loadEnvHealth: () => Promise<void>;
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
  }) => Promise<TargetEnv | null>;
}

export const useTargetEnvStore = create<TargetEnvState>((set, get) => ({
  envs: [],
  deployments: [],
  envHealth: {},
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