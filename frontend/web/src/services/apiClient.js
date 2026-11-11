import { isRemoteAuth } from '../features/auth/config.js';
import { readSession, clearSession } from '../features/auth/session.js';

const API_BASE = import.meta.env?.VITE_API_BASE_URL || '/api/v1';

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
  const url = path.startsWith('http')
    ? path
    : `${baseURL.replace(/\/$/, '')}/${path.replace(/^\//, '')}`;

  const storedToken = auth ? (token ?? (isRemoteAuth ? readSession()?.accessToken : localStorage.getItem('auth_token'))) : null;

  const response = await fetch(url, {
    ...options,
    signal: options.signal ? AbortSignal.any([options.signal, AbortSignal.timeout(15000)]) : AbortSignal.timeout(15000),
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(storedToken ? { Authorization: `Bearer ${storedToken}` } : {}),
      ...headers,
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });

  if (!response.ok) {
    let errorBody;
    try {
      errorBody = await response.json();
    } catch {
      errorBody = { message: response.statusText };
    }
    // Login's 401 INVALID_CREDENTIALS is not an expired session. Password mismatch is 422.
    if (isRemoteAuth && storedToken && response.status === 401
        && ['INVALID_TOKEN', 'UNAUTHENTICATED'].includes(errorBody?.code)) {
      clearSession(storedToken);
    }
    throw new ApiError(response.status, errorBody);
  }

  if (response.status === 204) return null;
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

export const healthApi = {
  check: () => apiRequest('/health', { baseURL: '/api', auth: false }),
};
