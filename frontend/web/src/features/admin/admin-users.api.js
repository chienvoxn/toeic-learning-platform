import { apiRequest } from '../../services/apiClient.js';

export const adminUsersApi = {
  list({ q = '', role = '', status = '', page = 0, size = 20 } = {}, signal) {
    const query = new URLSearchParams({ page: String(page), size: String(size) });
    if (q.trim()) query.set('q', q.trim());
    if (role) query.set('role', role);
    if (status) query.set('status', status);
    return apiRequest('/admin/users?' + query, { signal });
  },
  changeStatus(id, status) {
    return apiRequest('/admin/users/' + encodeURIComponent(id) + '/status', { method: 'PATCH', body: { status } });
  },
};
