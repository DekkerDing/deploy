import type { ReactNode, ElementType } from "react";

export function StatCard({ icon: Icon, title, value, color }: { icon: ElementType; title: string; value: string; color: string }) {
  return (
    <div className="card flex items-start gap-4" style={{ minWidth: 180 }}>
      <div className="flex h-10 w-10 items-center justify-center rounded-lg" style={{ backgroundColor: color + "18" }}>
        <Icon size={22} style={{ color }} />
      </div>
      <div>
        <div className="text-2xl font-bold">{value}</div>
        <div className="text-sm text-gray-500 dark:text-gray-400">{title}</div>
      </div>
    </div>
  );
}

export function LoadingSpinner() {
  return (<div className="flex items-center justify-center py-16"><div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" /></div>);
}

export function ErrorMessage({ message }: { message: string }) {
  return (<div className="flex items-center justify-center py-16"><div className="rounded-lg border border-danger/30 bg-danger/10 px-6 py-3 text-sm text-danger">加载失败: {message}</div></div>);
}

export function Badge({ children, color }: { children: ReactNode; color: string }) {
  return <span className="badge" style={{ backgroundColor: color + "18", color }}>{children}</span>;
}

export function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + " MB";
  return (bytes / 1024 / 1024 / 1024).toFixed(2) + " GB";
}

export function formatDate(iso: string, short?: boolean): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  const ymd = d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate());
  if (short) return ymd;
  return ymd + " " + pad(d.getHours()) + ":" + pad(d.getMinutes()) + ":" + pad(d.getSeconds());
}

export const STATE_MAP: Record<string, { label: string; color: string }> = {
  CREATED: { label: "已创建", color: "#6b7280" },
  BUILDING: { label: "构建中", color: "#f59e0b" },
  BUILT: { label: "构建完成", color: "#60a5fa" },
  DEPLOYING: { label: "部署中", color: "#ea580c" },
  DEPLOYED: { label: "已部署", color: "#10b981" },
  FAILED: { label: "失败", color: "#ef4444" },
  ROLLED_BACK: { label: "已回滚", color: "#8b5cf6" },
};