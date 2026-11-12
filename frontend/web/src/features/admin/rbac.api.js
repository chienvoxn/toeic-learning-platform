import { apiRequest } from '../../services/apiClient.js';

const rolePath = id => '/admin/roles/' + encodeURIComponent(id);
const userPath = id => '/admin/users/' + encodeURIComponent(id) + '/roles';
function query({ q = '', page = 0, size = 20 } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (q.trim()) params.set('q', q.trim());
  return '?' + params;
}
export const rbacApi = {
  roles: (filter, signal) => apiRequest('/admin/roles' + query(filter), { signal }),
  role: (id, signal) => apiRequest(rolePath(id), { signal }),
  permissions: signal => apiRequest('/admin/permissions', { signal }),
  users: (filter, signal) => apiRequest('/admin/rbac/users' + query(filter), { signal }),
  user: (id, signal) => apiRequest(userPath(id), { signal }),
  create: ({ name, displayName, description }) => apiRequest('/admin/roles', { method: 'POST', body: { name, displayName, description } }),
  edit: (id, { name, displayName, description }) => apiRequest(rolePath(id), { method: 'PATCH', body: { ...(name === undefined ? {} : { name }), displayName, description } }),
  remove: id => apiRequest(rolePath(id), { method: 'DELETE' }),
  setPermissions: (id, permissionIds) => apiRequest(rolePath(id) + '/permissions', { method: 'PUT', body: { permissionIds } }),
  setRoles: (id, roleIds) => apiRequest(userPath(id), { method: 'PUT', body: { roleIds } }),
};
