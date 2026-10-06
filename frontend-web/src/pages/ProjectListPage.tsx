import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { FolderOpen, Plus, ChevronRight } from "lucide-react";
import { useProjectStore } from "../stores/projectStore";
import { LoadingSpinner, ErrorMessage } from "../components/common";

export default function ProjectListPage() {
  const navigate = useNavigate();
  const { projects, loading, error, loadProjects, createProject } = useProjectStore();
  const [showCreate, setShowCreate] = useState(false);
  useEffect(() => { loadProjects(); }, [loadProjects]);

  const handleCreate = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const p = await createProject({ name: fd.get("name") as string, buildType: fd.get("buildType") as string, sourcePath: fd.get("sourcePath") as string, description: fd.get("description") as string || undefined });
    if (p) { setShowCreate(false); navigate("/projects/"+p.id); }
  };

  return (<div className="p-6 space-y-4"><div className="flex items-center justify-between"><h1 className="text-2xl font-bold">项目管理</h1><button onClick={() => setShowCreate(true)} className="btn-primary"><Plus size={16} /> 新建项目</button></div>
  {error && <ErrorMessage message={error} />}
  {loading ? <LoadingSpinner /> : projects.length===0 ? <p className="py-16 text-center text-gray-400">暂无项目，点击右上角新建</p> : <div className="space-y-3">{projects.map(p=>(<div key={p.id} className="card flex cursor-pointer items-center gap-4 hover:bg-gray-50 dark:hover:bg-gray-700/50" onClick={()=>navigate("/projects/"+p.id)}><div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10"><FolderOpen size={20} className="text-primary" /></div><div className="flex-1 min-w-0"><div className="font-semibold">{p.name}</div><div className="text-xs text-gray-500">{p.buildType} · {p.sourcePath}</div></div><ChevronRight size={18} className="text-gray-400" /></div>))}</div>}
  {showCreate && <CreateDialog onClose={()=>setShowCreate(false)} onSubmit={handleCreate} />}
  </div>);
}

function CreateDialog({onClose,onSubmit}:{onClose:()=>void;onSubmit:(e:React.FormEvent<HTMLFormElement>)=>void}) {
  return (<div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={onClose}><div className="card w-full max-w-md" onClick={e=>e.stopPropagation()}><h2 className="mb-4 text-lg font-bold">新建项目</h2><form onSubmit={onSubmit} className="space-y-3"><input name="name" className="input-field" placeholder="项目名称 *" required /><select name="buildType" className="input-field" defaultValue="MAVEN"><option value="MAVEN">Maven</option><option value="GRADLE">Gradle</option><option value="NPM">NPM</option><option value="FLUTTER">Flutter (APK)</option></select><input name="sourcePath" className="input-field" placeholder="源码路径 *" required /><input name="description" className="input-field" placeholder="描述（可选）" /><div className="flex justify-end gap-2 pt-2"><button type="button" onClick={onClose} className="btn-secondary">取消</button><button type="submit" className="btn-primary">创建</button></div></form></div></div>);
}
