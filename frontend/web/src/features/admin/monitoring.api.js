import { apiRequest } from '../../services/apiClient.js';

export const auditActions = ['USER_STATUS_CHANGED', 'ROLE_CREATED', 'ROLE_UPDATED', 'ROLE_DELETED', 'ROLE_PERMISSIONS_CHANGED', 'USER_ROLES_CHANGED'];
function query(input, keys) {
  const result = new URLSearchParams();
  for (const key of keys) if (input[key] !== undefined && String(input[key]).trim() !== '') result.set(key, String(input[key]).trim());
  return result.size ? '?' + result : '';
}
export const monitoringApi = {
  audit: (filter = {}, signal) => apiRequest('/admin/audit-logs' + query(filter, ['actor', 'action', 'entityType', 'entityId', 'from', 'to', 'page', 'size', 'sort']), { signal }),
  detail: (id, signal) => apiRequest('/admin/audit-logs/' + encodeURIComponent(id), { signal }),
  health: (_query, signal) => apiRequest('/admin/health', { signal }),
  usage: (filter = {}, signal) => apiRequest('/admin/usage-statistics' + query(filter, ['from', 'to']), { signal }),
};
