import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { Package, Hammer, Rocket, RotateCcw, Download, Server } from "lucide-react";
import { useReleaseStore } from "../stores/releaseStore";
import { useTargetEnvStore } from "../stores/targetEnvStore";
import { getSession } from "../core/auth";
import { LoadingSpinner, ErrorMessage, Badge, formatSize, formatDate, STATE_MAP } from "../components/common";
import BuildLogPanel from "../components/BuildLogPanel";

export default function ReleaseDetailPage() {
  const { id } = useParams<{ id: string }>();
  const releaseId = Number(id);
  const { detail, loading, error, loadDetail, loadDeployments, deployments, triggerBuild, deploy, rollback } = useReleaseStore();
  const { envs, loadEnvs } = useTargetEnvStore();
  const [actionMsg, setActionMsg] = useState<string | null>(null);
  const [showEnvDialog, setShowEnvDialog] = useState<"deploy" | "rollback" | null>(null);
  const [watching, setWatching] = useState(false);

  useEffect(() => {
    if (releaseId) { loadDetail(releaseId); loadDeployments(releaseId); loadEnvs(); }
  }, [releaseId, loadDetail, loadDeployments, loadEnvs]);

  // 部署/回滚进度轮询：终态即停并刷新部署记录（setTimeout 链，避免响应慢时请求堆叠）
  useEffect(() => {
    if (!watching || !detail) return;
    const st = detail.release.state;
    if (st === "DEPLOYED" || st === "FAILED" || st === "ROLLED_BACK") {
      setWatching(false);
      loadDeployments(releaseId);
      return;
    }
    const t = window.setTimeout(() => loadDetail(releaseId), 2000);
    return () => window.clearTimeout(t);
  }, [watching, detail, releaseId, loadDetail, loadDeployments]);

  if (loading || !detail) return <LoadingSpinner />;
  if (error) return <ErrorMessage message={error} />;

  const { release, artifacts, timeline: events } = detail;
  const s = STATE_MAP[release.state] || { label: release.state, color: "#6b7280" };

  return (
    <div className="p-6 space-y-4">
      {actionMsg && (
        <div className="flex items-center justify-between rounded-lg border border-accent/30 bg-accent/10 px-4 py-2 text-sm text-accent-dark">
          {actionMsg}
          <button onClick={() => setActionMsg(null)} className="text-gray-400 hover:text-gray-600">&times;</button>
        </div>
      )}

      <div className="card">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
              <Package size={20} className="text-primary" />
            </div>
            <div>
              <div className="text-lg font-bold">Release v{release.version}</div>
              <Badge color={s.color}>{s.label}</Badge>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {watching && <span className="animate-pulse text-xs text-amber-600">执行中（{s.label}）…</span>}
            {release.state === "CREATED" && (
              <button onClick={async () => { setActionMsg("触发构建中..."); await triggerBuild(releaseId); setActionMsg("构建已触发"); loadDetail(releaseId); }} className="btn-primary">
                <Hammer size={16} /> 触发构建
              </button>
            )}
            {release.state === "BUILT" && (
              <button onClick={() => setShowEnvDialog("deploy")} className="btn-primary">
                <Rocket size={16} /> 部署
              </button>
            )}
            {release.state === "DEPLOYED" && (
              <button onClick={() => setShowEnvDialog("rollback")} className="btn-secondary">
                <RotateCcw size={16} /> 回滚
              </button>
            )}
          </div>
        </div>
        {release.failReason && (
          <div className="mt-3 rounded-lg border border-danger/30 bg-danger/10 px-4 py-2 text-sm text-danger">失败原因: {release.failReason}</div>
        )}
        <div className="mt-3 grid grid-cols-2 gap-2 text-sm">
          <div><span className="text-gray-500">创建时间</span> {formatDate(release.createdAt)}</div>
          <div><span className="text-gray-500">更新时间</span> {formatDate(release.updatedAt)}</div>
        </div>
      </div>

      <BuildLogPanel releaseId={releaseId} />

      <div className="card">
        <h2 className="mb-3 text-lg font-bold">制品 ({artifacts.length})</h2>
        {artifacts.length === 0 ? <p className="py-8 text-center text-sm text-gray-400">暂无制品</p> : (
          <div className="divide-y dark:divide-gray-700">
            {artifacts.map((a) => (
              <div key={a.id} className="flex items-center gap-3 py-3">
                <Download size={18} className="text-gray-400" />
                <div className="flex-1 min-w-0">
                  <div className="font-medium text-sm">{a.fileName}</div>
                  <div className="text-xs text-gray-500">
                    {formatSize(a.sizeBytes)}{a.platformOs ? ` | ${a.platformOs}/${a.platformArch || "-"}${a.platformLibc ? "-" + a.platformLibc : ""}` : ""}{a.portable ? " | 便携" : ""}
                    {" | sha256: "}<span className="font-mono" title={a.sha256}>{a.sha256.slice(0, 12)}…</span>
                  </div>
                </div>
                <a
                  href={`/api/artifacts/${a.id}/download${getSession() ? `?session=${encodeURIComponent(getSession()!)}` : ''}`}
                  className="btn-secondary !py-1.5 !px-3 text-xs"
                >
                  下载
                </a>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="card">
        <h2 className="mb-3 text-lg font-bold">事件日志</h2>
        <div className="space-y-2">
          {events.map((ev) => {
            const st = STATE_MAP[ev.toState] || { label: ev.toState, color: "#6b7280" };
            return (
              <div key={ev.id} className="flex items-center gap-3 rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-700/30">
                <Badge color={st.color}>{st.label}</Badge>
                {ev.message && <span className="text-xs text-gray-600 dark:text-gray-400">{ev.message}</span>}
                <span className="ml-auto text-xs text-gray-400">{formatDate(ev.createdAt)}</span>
              </div>
            );
          })}
        </div>
      </div>

      <div className="card">
        <h2 className="mb-3 text-lg font-bold">部署记录</h2>
        {deployments.length === 0 ? <p className="py-8 text-center text-sm text-gray-400">暂无部署</p> : (
          <div className="divide-y dark:divide-gray-700">
            {deployments.map((d) => {
              const env = envs.find((e) => e.id === d.targetEnvId);
              const secs = d.finishedAt ? Math.max(1, Math.round((new Date(d.finishedAt).getTime() - new Date(d.startedAt).getTime()) / 1000)) : null;
              return (
                <div key={d.id} className="flex items-center gap-3 py-3">
                  <Server size={18} className="text-gray-400" />
                  <div className="flex-1 min-w-0">
                    <div className="text-sm font-medium">{env ? env.name : "环境 #" + d.targetEnvId}</div>
                    <div className="text-xs text-gray-500">{formatDate(d.startedAt)}{secs !== null ? ` · 耗时 ${secs}s` : " · 进行中…"}</div>
                    {d.message && <div className="truncate text-xs text-gray-400" title={d.message}>{d.message}</div>}
                  </div>
                  <Badge color={d.result === "SUCCESS" ? "#10b981" : d.result === "FAILED" ? "#ef4444" : d.result === "ROLLED_BACK" ? "#8b5cf6" : "#6b7280"}>{d.result}</Badge>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {showEnvDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={() => setShowEnvDialog(null)}>
          <div className="card w-full max-w-md" onClick={(e) => e.stopPropagation()}>
            <h2 className="mb-3 text-lg font-bold">{showEnvDialog === "deploy" ? "部署到目标环境" : "回滚目标环境"}</h2>
            {envs.length === 0 ? (
              <p className="py-8 text-center text-sm text-gray-400">暂无目标环境，请先在"目标环境"页注册</p>
            ) : (
              <div className="divide-y dark:divide-gray-700">
                {envs.map((env) => (
                  <button
                    key={env.id}
                    className="flex w-full items-center gap-3 py-3 text-left hover:bg-gray-50 dark:hover:bg-gray-700/30"
                    onClick={async () => {
                      const kind = showEnvDialog;
                      setShowEnvDialog(null);
                      setWatching(true);
                      const r = kind === "deploy" ? await deploy(releaseId, env.id) : await rollback(releaseId, env.id);
                      if (!r) setWatching(false);
                      setActionMsg(r ? `${kind === "deploy" ? "部署" : "回滚"}已触发 → ${env.name}` : `${kind === "deploy" ? "部署" : "回滚"}请求失败`);
                      loadDetail(releaseId);
                    }}
                  >
                    <Server size={18} className="text-gray-400" />
                    <div className="flex-1">
                      <div className="text-sm font-medium">{env.name}</div>
                      <div className="text-xs text-gray-500">{env.os}/{env.arch} · {env.runtime}{env.healthCheckPort ? ` · 健康端口 ${env.healthCheckPort}` : ""}</div>
                    </div>
                  </button>
                ))}
              </div>
            )}
            <div className="mt-3 flex justify-end"><button onClick={() => setShowEnvDialog(null)} className="btn-secondary">取消</button></div>
          </div>
        </div>
      )}
    </div>
  );
}