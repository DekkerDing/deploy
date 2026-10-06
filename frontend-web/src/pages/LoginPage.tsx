import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { LayoutDashboard } from 'lucide-react';
import { apiPost, ApiException } from '../core/api';
import { setSession } from '../core/auth';

/** 令牌登录页（specs/console-auth）：成功后会话入 localStorage 并进仪表盘。 */
export default function LoginPage() {
  const navigate = useNavigate();
  const [token, setToken] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!token.trim() || loading) return;
    setLoading(true);
    setError('');
    try {
      const res = await apiPost<{ sessionId: string }>('/api/auth/login', {
        token: token.trim(),
      });
      setSession(res.sessionId);
      navigate('/', { replace: true });
    } catch (err) {
      // 后端消息可读（"令牌不匹配"/"平台未启用认证…"），直接呈现
      setError(err instanceof ApiException ? err.message : '登录失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex h-screen items-center justify-center bg-gray-50 dark:bg-gray-900">
      <form
        onSubmit={submit}
        className="w-full max-w-sm rounded-xl border border-gray-200 bg-white p-8 shadow-sm dark:border-gray-700 dark:bg-gray-800"
      >
        <div className="mb-6 flex flex-col items-center gap-2">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary">
            <LayoutDashboard size={24} className="text-white" />
          </div>
          <h1 className="text-lg font-bold text-gray-900 dark:text-white">Deploy Platform</h1>
          <p className="text-xs text-gray-400">请输入平台令牌登录控制台</p>
        </div>
        <label className="mb-1 block text-xs font-medium text-gray-500 dark:text-gray-400">
          平台令牌
        </label>
        <input
          type="password"
          value={token}
          onChange={(e) => setToken(e.target.value)}
          autoFocus
          placeholder="deploy.auth.token 配置的令牌"
          className="mb-4 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-primary focus:outline-none dark:border-gray-600 dark:bg-gray-700 dark:text-white"
        />
        {error && (
          <div className="mb-4 rounded-lg bg-red-50 px-3 py-2 text-xs text-red-600 dark:bg-red-900/30 dark:text-red-400">
            {error}
          </div>
        )}
        <button
          type="submit"
          disabled={loading || !token.trim()}
          className="w-full rounded-lg bg-primary py-2 text-sm font-medium text-white transition-opacity hover:opacity-90 disabled:opacity-50"
        >
          {loading ? '登录中…' : '登录'}
        </button>
      </form>
    </div>
  );
}
