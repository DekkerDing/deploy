/** 平台会话存取（specs/console-auth）：后端启用认证时携带 X-Auth-Session；兼容模式无会话亦可访问。 */
const SESSION_KEY = 'deploy.auth.session';

export function getSession(): string | null {
  try {
    return localStorage.getItem(SESSION_KEY);
  } catch {
    return null;
  }
}

export function setSession(sessionId: string): void {
  try {
    localStorage.setItem(SESSION_KEY, sessionId);
  } catch {
    // localStorage 不可用（隐私模式等）：会话仅存活于当前内存，刷新后需重登
  }
}

export function clearSession(): void {
  try {
    localStorage.removeItem(SESSION_KEY);
  } catch {
    // 忽略
  }
}
