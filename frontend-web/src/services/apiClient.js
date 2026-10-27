const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';
export class ApiError extends Error {
  constructor(status, body) { super(body?.message || `HTTP ${status}`); this.name = 'ApiError'; this.status = status; this.code = body?.code; this.details = body?.details; }
}
export async function apiRequest(path, { token, headers = {}, body, ...options } = {}) {
  const url = `${API_BASE.replace(/\/$/, '')}/${path.replace(/^\//, '')}`;
  const response = await fetch(url, {
    ...options,
    headers: { ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });
  if (!response.ok) {
    let errorBody;
    try { errorBody = await response.json(); } catch { errorBody = { message: response.statusText }; }
    throw new ApiError(response.status, errorBody);
  }
  if (response.status === 204) return null;
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}
export const healthApi = { check: () => apiRequest('/health') };
