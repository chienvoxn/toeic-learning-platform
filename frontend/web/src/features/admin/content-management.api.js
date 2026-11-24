import { apiRequest } from '../../services/apiClient.js';

const paths = { contents: '/teacher/contents', questions: '/teacher/questions', groups: '/teacher/question-groups' };
function path(kind) {
  if (!Object.hasOwn(paths, kind)) throw new TypeError('Loại nội dung không hợp lệ.');
  return paths[kind];
}
export const contentManagementApi = {
  list: (kind, filter = {}, signal) => {
    const params = new URLSearchParams();
    for (const key of ['status', 'page', 'size']) if (filter[key] !== undefined && String(filter[key]) !== '') params.set(key, filter[key]);
    return apiRequest(path(kind) + (params.size ? '?' + params : ''), { signal });
  },
  detail: (kind, id, signal) => apiRequest(path(kind) + '/' + encodeURIComponent(id), { signal }),
  options: (_query, signal) => apiRequest('/teacher/question-options', { signal }),
  create: (kind, body) => apiRequest(path(kind), { method: 'POST', body }),
  update: (kind, id, body) => apiRequest(path(kind) + '/' + encodeURIComponent(id), { method: 'PATCH', body }),
  archive: (kind, id) => apiRequest(path(kind) + '/' + encodeURIComponent(id), { method: 'DELETE' }),
};
