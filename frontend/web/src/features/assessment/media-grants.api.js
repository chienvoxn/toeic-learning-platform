import { apiRequest } from '@/services/apiClient';
const root = '/teacher/media';
export const mediaGrantsApi = {
  owned: (page, signal) => apiRequest(`${root}?page=${page}&size=20`, { signal }),
  grants: (id, page, signal) => apiRequest(`${root}/${encodeURIComponent(id)}/reuse-grants?page=${page}&size=20`, { signal }),
  change: (id, user, active) => apiRequest(`${root}/${encodeURIComponent(id)}/reuse-grants/${encodeURIComponent(user)}`, { method: active ? 'PUT' : 'DELETE' }),
};
