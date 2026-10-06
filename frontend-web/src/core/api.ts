import axios, { AxiosError } from 'axios';
import { config } from './config';

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

client.interceptors.response.use(
  (res) => res,
  (err: AxiosError<{ message?: string; msg?: string }>) => {
    const status = err.response?.status ?? 0;
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