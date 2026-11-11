import { isRemoteAuth } from '../features/auth/config.js';
import { readSession, clearSession } from '../features/auth/session.js';

const API_BASE = (import.meta.env?.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '').replace(/\/api$/, '/api/v1');

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = body?.code;
    this.details = body?.details;
    this.traceId = body?.traceId;
  }
}

export async function apiRequest(path, { token, auth = true, baseURL = API_BASE, headers = {}, body, ...options } = {}) {
  const url = path.startsWith('http') || path.startsWith('/api/')
    ? path : `${baseURL.replace(/\/$/, '')}/${path.replace(/^\//, '')}`;
  // token:null is the team's anonymous-request convention; auth:false is also explicit.
  const storedToken = !auth || token === null ? null
    : token ?? (isRemoteAuth ? readSession()?.accessToken : localStorage.getItem('auth_token'));
  let response;
  try {
    response = await fetch(url, {
      ...options,
      signal: options.signal ? AbortSignal.any([options.signal, AbortSignal.timeout(15000)]) : AbortSignal.timeout(15000),
      headers: {
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(storedToken ? { Authorization: `Bearer ${storedToken}` } : {}),
        ...headers,
      },
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
    });
  } catch (error) {
    if (error.name === 'AbortError') throw error;
    throw new ApiError(0, { code: 'NETWORK_ERROR', message: 'Không kết nối được máy chủ. Vui lòng kiểm tra backend và thử lại.' });
  }
  if (!response.ok) {
    let errorBody;
    try { errorBody = await response.json(); }
    catch { errorBody = { message: response.statusText }; }
    if (isRemoteAuth && storedToken && response.status === 401
        && ['INVALID_TOKEN', 'UNAUTHENTICATED', 'TOKEN_EXPIRED'].includes(errorBody?.code)) clearSession(storedToken);
    throw new ApiError(response.status, errorBody);
  }
  if (response.status === 204) return null;
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

export const healthApi = {
  check: (options = {}) => apiRequest('/api/health', { ...options, auth: false }),
  database: (options = {}) => apiRequest('/api/health/database', { ...options, auth: false }),
};
