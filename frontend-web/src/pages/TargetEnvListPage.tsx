import { useEffect, useState } from "react";
import { Monitor, Plus, Circle } from "lucide-react";
import { useTargetEnvStore } from "../stores/targetEnvStore";
import { LoadingSpinner, ErrorMessage } from "../components/common";

export default function TargetEnvListPage() {
  const { envs, loading, error, loadEnvs, register, envHealth, loadEnvHealth } = useTargetEnvStore();
  useEffect(() => { loadEnvs(); }, [loadEnvs]);
  useEffect(() => { if (envs.length > 0) loadEnvHealth(); }, [envs, loadEnvHealth]);
  const [showRegister, setShowRegister] = useState(false);

  const handleRegister = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    await register({
      name: fd.get("name") as string,
      os: fd.get("os") as string,
      arch: fd.get("arch") as string,
      runtimeType: fd.get("runtimeType") as string,
      reach: fd.get("reach") as string,
      host: (fd.get("host") as string) || undefined,
      port: Number(fd.get("port")) || undefined,
      username: (fd.get("username") as string) || undefined,
      credential: (fd.get("credential") as string) || undefined,
      jvmVersion: Number(fd.get("jvmVersion")) || undefined,
      healthCheckPort: Number(fd.get("healthCheckPort")) || undefined,
    });
    setShowRegister(false);
  };

  return (<div className="p-6 space-y-4"><div className="flex items-center justify-between"><h1 className="text-2xl font-bold">目标环境</h1><button onClick={()=>setShowRegister(true)} className="btn-primary"><Plus size={16} /> 注册环境</button></div>
  {error && <ErrorMessage message={error} />}
  {loading ? <LoadingSpinner /> : envs.length===0 ? <p className="py-16 text-center text-gray-400">暂无目标环境</p> :
    <div className="space-y-3">{envs.map(e=>(<div key={e.id} className="card space-y-3"><div className="flex items-center gap-3"><div className="flex h-10 w-10 items-center justify-center rounded-lg bg-accent/10"><Monitor size={20} className="text-accent" /></div><div className="flex-1"><div className="font-semibold">{e.name}</div><div className="text-xs text-gray-500">{e.os}/{e.arch} · {e.runtime}</div></div><span className={"badge text-xs "+(e.probeStatus==="KNOWN"?"bg-green-100 text-green-700":"bg-gray-100 text-gray-600")}><Circle size={6} className="fill-current" /> {e.probeStatus==="KNOWN"?"可达":"未知"}</span>
    {(() => { const last = envHealth[e.id]; const cls = last?.result==="SUCCESS" ? "bg-green-100 text-green-700" : last?.result==="FAILED" ? "bg-red-100 text-red-700" : "bg-gray-100 text-gray-500"; const label = last ? (last.result==="SUCCESS"?"健康":last.result==="FAILED"?"部署失败":"已回滚") : "未部署"; return <span className={"badge text-xs "+cls} title={last?.message || undefined}><Circle size={6} className="fill-current" /> {label}</span>; })()}</div>
  {e.host && <div className="grid grid-cols-2 gap-1 text-xs"><span className="text-gray-500">主机: {e.host}:{e.port}</span><span className="text-gray-500">连接: {e.reach}</span>{e.jvmVersion&&<span className="text-gray-500">JVM v{e.jvmVersion}</span>}{e.healthCheckPort&&<span className="text-gray-500">端口: {e.healthCheckPort}</span>}</div>}
  </div>))}</div>}
  {showRegister && <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={()=>setShowRegister(false)}><div className="card w-full max-w-lg max-h-[90vh] overflow-auto" onClick={e=>e.stopPropagation()}><h2 className="mb-4 text-lg font-bold">注册目标环境</h2><form onSubmit={handleRegister} className="space-y-3"><input name="name" className="input-field" placeholder="名称 *" required /><div className="grid grid-cols-2 gap-3"><select name="os" className="input-field" defaultValue="linux"><option value="linux">Linux</option><option value="windows">Windows</option><option value="darwin">macOS</option></select><select name="arch" className="input-field" defaultValue="amd64"><option value="amd64">AMD64</option><option value="arm64">ARM64</option><option value="armv7">ARMv7</option></select></div><div className="grid grid-cols-2 gap-3"><select name="runtimeType" className="input-field" defaultValue="JVM"><option value="JVM">JVM</option><option value="DOCKER">Docker</option><option value="K8S">Kubernetes</option><option value="NATIVE">Native</option></select><select name="reach" className="input-field" defaultValue="SSH"><option value="SSH">SSH</option><option value="WINRM">WinRM</option><option value="LOCAL">本地</option></select></div><div className="grid grid-cols-2 gap-3"><input name="host" className="input-field" placeholder="主机" /><input name="port" className="input-field" placeholder="端口" type="number" /></div><div className="grid grid-cols-2 gap-3"><input name="username" className="input-field" placeholder="用户名" /><input name="credential" className="input-field" placeholder="密码/私钥" type="password" /></div><div className="grid grid-cols-2 gap-3"><input name="jvmVersion" className="input-field" placeholder="JVM 版本" type="number" /><input name="healthCheckPort" className="input-field" placeholder="健康检查端口" type="number" /></div><div className="flex justify-end gap-2 pt-2"><button type="button" onClick={()=>setShowRegister(false)} className="btn-secondary">取消</button><button type="submit" className="btn-primary">注册</button></div></form></div></div>}
  </div>);
}
