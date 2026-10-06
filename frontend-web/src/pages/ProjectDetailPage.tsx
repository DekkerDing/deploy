import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { FolderOpen, Plus } from "lucide-react";
import { useProjectStore } from "../stores/projectStore";
import { useReleaseStore } from "../stores/releaseStore";
import { LoadingSpinner, ErrorMessage, Badge, formatDate, STATE_MAP } from "../components/common";
import type { Project } from "../models/project";

export default function ProjectDetailPage() {
  const { id } = useParams<{id:string}>();
  const navigate = useNavigate();
  const projectId = Number(id);
  const { projects, loadProjects } = useProjectStore();
  const { releases, loading, error, loadByProject, createRelease } = useReleaseStore();
  const [project, setProject] = useState<Project|null>(null);
  const [showCreate, setShowCreate] = useState(false);

  useEffect(() => {
    (async() => { await loadProjects(); await loadByProject(projectId); })();
  }, [loadProjects, loadByProject, projectId]);
  useEffect(() => {
    setProject(projects.find(p => p.id === projectId) || null);
  }, [projects, projectId]);

  const handleCreate = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const v = fd.get("version") as string;
    const r = await createRelease(projectId, v);
    if (r) { setShowCreate(false); navigate("/releases/" + r.id); }
  };

  if (!project) return <LoadingSpinner />;
  return (
    <div className="p-6 space-y-4">
      <div className="card">
        <div className="flex items-start gap-4">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10">
            <FolderOpen size={26} className="text-primary" />
          </div>
          <div className="flex-1">
            <h1 className="text-xl font-bold">{project.name}</h1>
            <p className="text-sm text-primary-light">{project.buildType}</p>
          </div>
          <button onClick={() => setShowCreate(true)} className="btn-primary">
            <Plus size={16} /> 发布新版本
          </button>
        </div>
        {project.description && (
          <p className="mt-3 text-sm text-gray-600 dark:text-gray-400">{project.description}</p>
        )}
        <div className="mt-4 grid grid-cols-2 gap-2 text-sm">
          <div><span className="text-gray-500">源码路径</span> <span>{project.sourcePath}</span></div>
          <div><span className="text-gray-500">创建时间</span> <span>{formatDate(project.createdAt)}</span></div>
        </div>
      </div>
      <div className="card">
        <h2 className="mb-3 text-lg font-bold">发布历史</h2>
        {loading ? <LoadingSpinner /> : error ? <ErrorMessage message={error} /> :
          releases.length === 0 ? <p className="py-8 text-center text-gray-400">暂无发布记录</p> :
            <div className="divide-y dark:divide-gray-700">
              {releases.map(r => {
                const s = STATE_MAP[r.state] || { label: r.state, color: "#6b7280" };
                return (
                  <div key={r.id} className="flex cursor-pointer items-center gap-3 py-3 hover:bg-gray-50 dark:hover:bg-gray-700/50" onClick={() => navigate("/releases/" + r.id)}>
                    <Badge color={s.color}>{s.label}</Badge>
                    <span className="font-medium">v{r.version}</span>
                    <span className="ml-auto text-xs text-gray-400">{formatDate(r.createdAt, true)}</span>
                  </div>
                );
              })}
            </div>
        }
      </div>
      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={() => setShowCreate(false)}>
          <div className="card w-full max-w-sm" onClick={e => e.stopPropagation()}>
            <h2 className="mb-4 text-lg font-bold">创建发布</h2>
            <form onSubmit={handleCreate} className="space-y-3">
              <input name="version" className="input-field" placeholder="版本号 * (如 1.0.0)" required />
              <div className="flex justify-end gap-2 pt-2">
                <button type="button" onClick={() => setShowCreate(false)} className="btn-secondary">取消</button>
                <button type="submit" className="btn-primary">创建</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}