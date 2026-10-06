import axios, { AxiosError } from 'axios';
import { config } from './config';
import { clearSession, getSession } from './auth';

export class ApiException extends Error {
  constructor(
    public code: number,
    message: string
  ) {
    super(message);
    this.name = 'ApiException';
  }
}

const client = axios.create({
  baseURL: config.apiBaseUrl,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
});

// 会话标记注入：启用认证时后端校验；兼容模式（无会话）不受影响
client.interceptors.request.use((req) => {
  const session = getSession();
  if (session) {
    req.headers = req.headers ?? {};
    req.headers['X-Auth-Session'] = session;
  }
  return req;
});

client.interceptors.response.use(
  (res) => res,
  (err: AxiosError<{ message?: string; msg?: string }>) => {
    const status = err.response?.status ?? 0;
    // 会话缺失/失效：清除本地会话并跳登录页（登录请求自身的 401 除外，由登录页呈现错误）
    if (status === 401 && !err.config?.url?.includes('/api/auth/login')) {
      clearSession();
      window.location.href = '/login';
    }
    const data = err.response?.data;
    const message = data?.message ?? data?.msg ?? err.message;
    throw new ApiException(status, message);
  }
);

export async function apiGet<T = unknown>(path: string): Promise<T> {
  const res = await client.get<T>(path);
  return res.data;
}

export async function apiPost<T = unknown>(path: string, body?: unknown): Promise<T> {
  const res = await client.post<T>(path, body);
  return res.data;
}

export async function apiPut<T = unknown>(path: string, body?: unknown): Promise<T> {
  const res = await client.put<T>(path, body);
  return res.data;
}

export async function apiDelete(path: string): Promise<void> {
  await client.delete(path);
}