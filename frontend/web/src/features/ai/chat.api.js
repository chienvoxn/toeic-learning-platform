import { apiRequest } from '../../services/apiClient.js';

export const chatApi = {
  conversations: (signal, page = 0) => apiRequest(`/ai/conversations?page=${page}`, { signal }),
  create: () => apiRequest('/ai/conversations', { method: 'POST' }),
  remove: id => apiRequest(`/ai/conversations/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  messages: (id, signal, page = 0) => apiRequest(`/ai/conversations/${encodeURIComponent(id)}/messages?page=${page}`, { signal }),
  ask: (id, question, requestId, signal) => apiRequest(`/ai/conversations/${encodeURIComponent(id)}/messages`, {
    method: 'POST', body: { question, requestId }, signal, timeoutMs: 55000,
  }),
};
