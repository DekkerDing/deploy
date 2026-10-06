import { create } from 'zustand';
import { apiGet, apiPost } from '../core/api';
import { Project } from '../models/project';

interface ProjectState {
  projects: Project[];
  loading: boolean;
  error: string | null;

  loadProjects: () => Promise<void>;
  createProject: (data: {
    name: string;
    buildType: string;
    sourcePath: string;
    description?: string;
  }) => Promise<Project | null>;
}

export const useProjectStore = create<ProjectState>((set, get) => ({
  projects: [],
  loading: false,
  error: null,

  loadProjects: async () => {
    set({ loading: true, error: null });
    try {
      const data = await apiGet<Project[]>('/api/projects');
      set({ projects: data, loading: false });
    } catch (e) {
      set({ error: (e as Error).message, loading: false });
    }
  },

  createProject: async (body) => {
    set({ error: null });
    try {
      const project = await apiPost<Project>('/api/projects', body);
      set((s) => ({ projects: [...s.projects, project] }));
      return project;
    } catch (e) {
      set({ error: (e as Error).message });
      return null;
    }
  },
}));