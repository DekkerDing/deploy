import { create } from 'zustand';
import { apiGet, apiPost } from '../core/api';
import { Release, ReleaseDetail } from '../models/release';
import { Deployment } from '../models/deployment';

interface ReleaseState {
  releases: Release[];
  detail: ReleaseDetail | null;
  deployments: Deployment[];
  loading: boolean;
  error: string | null;

  loadByProject: (projectId: number) => Promise<void>;
  loadDetail: (releaseId: number) => Promise<void>;
  loadDeployments: (releaseId: number) => Promise<void>;
  createRelease: (projectId: number, version: string) => Promise<Release | null>;
  triggerBuild: (releaseId: number) => Promise<Release | null>;
  deploy: (releaseId: number, targetEnvId: number) => Promise<Release | null>;
  rollback: (releaseId: number, targetEnvId: number) => Promise<Release | null>;
}

export const useReleaseStore = create<ReleaseState>((set) => ({
  releases: [],
  detail: null,
  deployments: [],
  loading: false,
  error: null,

  loadByProject: async (projectId) => {
    set({ loading: true, error: null });
    try {
      const data = await apiGet<Release[]>(`/api/projects/${projectId}/releases`);
      set({ releases: data, loading: false });
    } catch (e) {
      set({ error: (e as Error).message, loading: false });
    }
  },

  loadDetail: async (releaseId) => {
    set({ loading: true, error: null });
    try {
      const data = await apiGet<ReleaseDetail>(`/api/releases/${releaseId}`);
      set({ detail: data, loading: false });
    } catch (e) {
      set({ error: (e as Error).message, loading: false });
    }
  },

  loadDeployments: async (releaseId) => {
    try {
      const data = await apiGet<Deployment[]>(`/api/releases/${releaseId}/deployments`);
      set({ deployments: data });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  createRelease: async (projectId, version) => {
    set({ error: null });
    try {
      const release = await apiPost<Release>(`/api/projects/${projectId}/releases`, { version });
      set((s) => ({ releases: [...s.releases, release] }));
      return release;
    } catch (e) {
      set({ error: (e as Error).message });
      return null;
    }
  },

  triggerBuild: async (releaseId) => {
    set({ error: null });
    try {
      const release = await apiPost<Release>(`/api/releases/${releaseId}/trigger`);
      set((s) => ({
        releases: s.releases.map((r) => (r.id === releaseId ? release : r)),
        detail: s.detail?.release.id === releaseId ? { ...s.detail, release } : s.detail,
      }));
      return release;
    } catch (e) {
      set({ error: (e as Error).message });
      return null;
    }
  },

  deploy: async (releaseId, targetEnvId) => {
    set({ error: null });
    try {
      const release = await apiPost<Release>(`/api/releases/${releaseId}/deploy`, { targetEnvId });
      return release;
    } catch (e) {
      set({ error: (e as Error).message });
      return null;
    }
  },

  rollback: async (releaseId, targetEnvId) => {
    set({ error: null });
    try {
      const release = await apiPost<Release>(`/api/releases/${releaseId}/rollback`, { targetEnvId });
      return release;
    } catch (e) {
      set({ error: (e as Error).message });
      return null;
    }
  },
}));