import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  LayoutDashboard,
  FolderOpen,
  Monitor,
  Menu,
  Moon,
  Sun,
  LogOut,
} from 'lucide-react';
import { clearSession, getSession } from '../core/auth';

export default function Layout() {
  const navigate = useNavigate();
  const [dark, setDark] = useState(() => {
    if (typeof window !== 'undefined') {
      return document.documentElement.classList.contains('dark');
    }
    return false;
  });
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const toggleDark = () => {
    setDark((prev) => {
      const next = !prev;
      document.documentElement.classList.toggle('dark', next);
      return next;
    });
  };

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${
      isActive
        ? 'bg-primary text-white'
        : 'text-gray-700 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-700'
    }`;

  return (
    <div className="flex h-screen overflow-hidden">
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-30 bg-black/40 lg:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}
      <aside
        className={`fixed inset-y-0 left-0 z-40 flex w-60 flex-col border-r border-gray-200 bg-white transition-transform dark:border-gray-700 dark:bg-gray-800 lg:static lg:translate-x-0 ${
          sidebarOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="flex h-14 items-center gap-3 border-b border-gray-200 px-4 dark:border-gray-700">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary">
            <LayoutDashboard size={18} className="text-white" />
          </div>
          <div>
            <div className="text-sm font-bold text-gray-900 dark:text-white">Deploy Platform</div>
            <div className="text-[10px] text-gray-400">Serverless CI/CD</div>
          </div>
        </div>
        <nav className="flex-1 space-y-1 p-3">
          <NavLink to="/" end className={linkClass} onClick={() => setSidebarOpen(false)}>
            <LayoutDashboard size={18} /> 仪表盘
          </NavLink>
          <NavLink to="/projects" className={linkClass} onClick={() => setSidebarOpen(false)}>
            <FolderOpen size={18} /> 项目管理
          </NavLink>
          <NavLink to="/target-envs" className={linkClass} onClick={() => setSidebarOpen(false)}>
            <Monitor size={18} /> 目标环境
          </NavLink>
        </nav>
        <div className="space-y-1 border-t border-gray-200 p-3 dark:border-gray-700">
          <button
            onClick={toggleDark}
            className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700"
          >
            {dark ? <Sun size={16} /> : <Moon size={16} />}
            {dark ? '浅色模式' : '深色模式'}
          </button>
          {getSession() && (
            <button
              onClick={() => {
                clearSession();
                navigate('/login');
              }}
              className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700"
            >
              <LogOut size={16} /> 退出登录
            </button>
          )}
        </div>
      </aside>
      <main className="flex flex-1 flex-col overflow-hidden">
        <header className="flex h-14 items-center gap-3 border-b border-gray-200 bg-white px-4 dark:border-gray-700 dark:bg-gray-800 lg:hidden">
          <button onClick={() => setSidebarOpen(true)} className="text-gray-600 dark:text-gray-300">
            <Menu size={22} />
          </button>
          <span className="text-sm font-bold">Deploy Platform</span>
        </header>
        <div className="flex-1 overflow-auto bg-gray-50 dark:bg-gray-900">
          <Outlet />
        </div>
      </main>
    </div>
  );
}