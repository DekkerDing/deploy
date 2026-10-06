import { ReactNode, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiGet } from '../core/api';
import { getSession } from '../core/auth';

/**
 * 路由守卫（specs/console-auth）：
 * 本地已有会话 → 直接放行；无会话时探测一次业务 API——
 * 401（未登录）由响应拦截器跳 /login；2xx 为认证关闭的兼容模式，无感放行。
 */
export default function RequireAuth({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [ok, setOk] = useState(() => getSession() !== null);

  useEffect(() => {
    if (ok) return;
    let cancelled = false;
    apiGet('/api/projects')
      .then(() => {
        if (!cancelled) setOk(true);
      })
      .catch(() => {
        // 401：拦截器已清会话并跳 /login；其他错误（网络等）放行，由页面自身呈现
        if (!cancelled) setOk(true);
      });
    return () => {
      cancelled = true;
    };
  }, [ok, navigate]);

  if (!ok) {
    return (
      <div className="flex h-screen items-center justify-center text-sm text-gray-400">
        正在检查登录状态…
      </div>
    );
  }
  return <>{children}</>;
}
