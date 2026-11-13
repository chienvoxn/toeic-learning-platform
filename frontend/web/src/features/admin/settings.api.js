import { apiRequest } from '../../services/apiClient.js';

export const settingsApi = {
  list: (_query, signal) => apiRequest('/admin/system-settings', { signal }),
  update: (key, value, expectedRevision) => apiRequest('/admin/system-settings/' + encodeURIComponent(key), { method: 'PATCH', body: { value, expectedRevision } }),
};
