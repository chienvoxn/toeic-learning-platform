const SESSION_KEY = 'toeic.remote.session';
export const SESSION_ENDED = 'identity:session-ended';
export const SESSION_KEY_NAME = SESSION_KEY;

export function readSession() {
  try {
    const value = JSON.parse(localStorage.getItem(SESSION_KEY));
    return typeof value?.accessToken === 'string' && value.tokenType === 'Bearer'
      && Number.isFinite(value.expiresAt) ? value : null;
  } catch { return null; }
}

export function saveSession({ accessToken, tokenType, expiresIn }) {
  if (!accessToken || tokenType !== 'Bearer' || !Number.isFinite(expiresIn) || expiresIn <= 0) {
    throw new Error('Phản hồi phiên đăng nhập không hợp lệ.');
  }
  localStorage.setItem(SESSION_KEY, JSON.stringify({ accessToken, tokenType, expiresAt: Date.now() + expiresIn * 1000 }));
  localStorage.removeItem('auth_user');
  localStorage.removeItem('auth_token');
}

export function clearSession(expectedToken) {
  if (expectedToken && readSession()?.accessToken !== expectedToken) return;
  localStorage.removeItem(SESSION_KEY);
  window.dispatchEvent(new Event(SESSION_ENDED));
}
