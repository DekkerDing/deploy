import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { FolderOpen, Monitor, CloudUpload, CheckCircle, Plus, MonitorUp, ArrowRight } from "lucide-react";
import { useProjectStore } from "../stores/projectStore";
import { useTargetEnvStore } from "../stores/targetEnvStore";
import { StatCard, LoadingSpinner, ErrorMessage } from "../components/common";

export default function DashboardPage() {
  const navigate = useNavigate();
  const { projects, loading: pLoading, error: pError, loadProjects } = useProjectStore();
  const { envs, loading: eLoading, loadEnvs } = useTargetEnvStore();
  useEffect(() => { loadProjects(); loadEnvs(); }, [loadProjects, loadEnvs]);
  if (pLoading && eLoading) return <LoadingSpinner />;
  return (
    <div className="p-6 space-y-6">
      <div><h1 className="text-2xl font-bold">Deploy Platform</h1><p className="text-sm text-gray-500 dark:text-gray-400">Serverless 部署平台 · 前后端一体化</p></div>
      {pError && <ErrorMessage message={pError} />}
      <div className="flex flex-wrap gap-4">
        <StatCard icon={FolderOpen} title="项目" value={pLoading ? "..." : String(projects.length)} color="#2563eb" />
        <StatCard icon={Monitor} title="目标环境" value={eLoading ? "..." : String(envs.length)} color="#10b981" />
        <StatCard icon={CloudUpload} title="构建中" value="0" color="#f59e0b" />
        <StatCard icon={CheckCircle} title="已部署" value="0" color="#60a5fa" />
      </div>
      <div className="card">
        <h2 className="mb-4 text-lg font-bold">快捷操作</h2>
        <div className="flex gap-3">
          <button onClick={() => navigate("/projects")} className="btn-primary"><Plus size={16} /> 新建项目</button>
          <button onClick={() => navigate("/target-envs")} className="btn-secondary"><MonitorUp size={16} /> 注册环境</button>
        </div>
      </div>
      <div className="card">
        <div className="mb-4 flex items-center justify-between"><h2 className="text-lg font-bold">项目列表</h2><button onClick={() => navigate("/projects")} className="text-sm text-primary hover:underline">查看全部</button></div>
        {projects.slice(0,5).map((p:any) => (<div key={p.id} className="flex cursor-pointer items-center gap-4 py-3 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700/50" onClick={() => navigate("/projects/"+p.id)}><div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary/10"><FolderOpen size={18} className="text-primary" /></div><div className="flex-1 min-w-0"><div className="font-medium">{p.name}</div><div className="text-xs text-gray-500 dark:text-gray-400">{p.buildType} · {p.sourcePath}</div></div><ArrowRight size={16} className="text-gray-400" /></div>))}
      </div>
    </div>
  );
}