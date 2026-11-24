// Mặc định địa chỉ BE API:
// - Android Emulator: 10.0.2.2:8080/api/v1
// - Web / iOS Simulator / Node test: localhost:8080/api/v1
const isAndroid =
  typeof globalThis !== "undefined" &&
  (globalThis.__DEV_ANDROID__ ||
    (typeof navigator !== "undefined" &&
      /android/i.test(navigator.userAgent || "")));

const DEFAULT_HOST = isAndroid
  ? "http://10.0.2.2:8080"
  : "http://localhost:8080";

export const API_BASE_URL = `${DEFAULT_HOST}/api/v1`;

let currentToken = null;

export const authStorage = {
  getToken: () => currentToken,
  setToken: (token) => {
    currentToken = token;
  },
  clearToken: () => {
    currentToken = null;
  },
};

export async function request(endpoint, options = {}) {
  const url = endpoint.startsWith("http")
    ? endpoint
    : `${API_BASE_URL}${endpoint.startsWith("/") ? "" : "/"}${endpoint}`;

  const headers = {
    "Content-Type": "application/json",
    Accept: "application/json",
    ...(options.headers || {}),
  };

  const token = authStorage.getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const controller = typeof AbortController !== "undefined" ? new AbortController() : null;
  const timeoutMs = options.timeout || 8000;
  const timeoutId = controller ? setTimeout(() => controller.abort(), timeoutMs) : null;

  try {
    const fetchOptions = {
      ...options,
      headers,
    };
    if (controller) {
      fetchOptions.signal = controller.signal;
    }

    const response = await fetch(url, fetchOptions);

    if (timeoutId) clearTimeout(timeoutId);

    if (!response.ok) {
      let errorBody = {};
      try {
        errorBody = await response.json();
      } catch {
        // parse error ignored
      }
      const error = new Error(
        errorBody.message || `Lỗi máy chủ (${response.status})`,
      );
      error.status = response.status;
      error.code = errorBody.code;
      throw error;
    }

    if (response.status === 204) {
      return null;
    }

    return await response.json();
  } catch (error) {
    if (timeoutId) clearTimeout(timeoutId);
    throw error;
  }
}
