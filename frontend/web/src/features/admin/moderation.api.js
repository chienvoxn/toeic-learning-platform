import { apiRequest } from '../../services/apiClient.js';
const kinds = new Set(['contents', 'questions', 'groups']);
function path(kind, parent, version) {
  if (!kinds.has(kind)) throw new TypeError('Loại moderation không hợp lệ.');
  return '/moderation/' + kind + (parent ? '/' + encodeURIComponent(parent) + '/versions/' + encodeURIComponent(version) : '');
}
export const moderationApi = {
  capabilities: (_query, signal) => apiRequest('/moderation/capabilities', { signal }),
  list: (query, signal) => apiRequest(path(query.kind) + '?page=' + query.page, { signal }),
  detail: (kind, parent, version) => apiRequest(path(kind, parent, version)),
  transition: (kind, parent, version, command, body) => apiRequest(path(kind, parent, version) + '/' + command, { method: 'POST', body }),
};
