import { apiRequest } from '../../services/apiClient.js';

export const auditActions = ['USER_STATUS_CHANGED', 'ROLE_CREATED', 'ROLE_UPDATED', 'ROLE_DELETED', 'ROLE_PERMISSIONS_CHANGED', 'USER_ROLES_CHANGED', 'SYSTEM_SETTING_UPDATED', 'CONTENT_ARCHIVED', 'QUESTION_ARCHIVED', 'QUESTION_GROUP_ARCHIVED', 'CONTENT_CREATED', 'CONTENT_UPDATED', 'QUESTION_CREATED', 'QUESTION_UPDATED', 'QUESTION_GROUP_CREATED', 'QUESTION_GROUP_UPDATED', 'CONTENT_SUBMITTED', 'CONTENT_APPROVED', 'CONTENT_RETURNED', 'CONTENT_APPROVAL_REVOKED', 'CONTENT_PUBLISHED', 'QUESTION_SUBMITTED', 'QUESTION_APPROVED', 'QUESTION_RETURNED', 'QUESTION_APPROVAL_REVOKED', 'QUESTION_PUBLISHED', 'QUESTION_GROUP_SUBMITTED', 'QUESTION_GROUP_APPROVED', 'QUESTION_GROUP_RETURNED', 'QUESTION_GROUP_APPROVAL_REVOKED', 'QUESTION_GROUP_PUBLISHED'];
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
