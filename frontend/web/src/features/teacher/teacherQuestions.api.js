
import { apiRequest } from '../../services/apiClient';

function listParams(filters = {}) {
  const params = new URLSearchParams({
    page: String(filters.page ?? 0),
    size: String(filters.size ?? 20),
    sort: 'updatedAt,desc',
  });

  if (filters.status) {
    params.set('status', filters.status);
  }

  if (filters.partId) {
    params.set('partId', String(filters.partId));
  }

  return params;
}

export const teacherQuestionsApi = {
  list(filters = {}) {
    return apiRequest(
      `/teacher/questions?${listParams(filters)}`
    );
  },

  detail(id) {
    return apiRequest(
      `/teacher/questions/${encodeURIComponent(id)}`
    );
  },

  history(id) {
    return apiRequest(
      `/teacher/questions/${encodeURIComponent(id)}/history`
    );
  },

  create(payload) {
    return apiRequest('/teacher/questions', {
      method: 'POST',
      body: payload,
    });
  },

  update(id, payload) {
    return apiRequest(
      `/teacher/questions/${encodeURIComponent(id)}`,
      {
        method: 'PATCH',
        body: payload,
      }
    );
  },

  archive(id) {
    return apiRequest(
      `/teacher/questions/${encodeURIComponent(id)}`,
      { method: 'DELETE' }
    );
  },

  listGroups({ page = 0, status = '' } = {}) {
    const params = new URLSearchParams({
      page: String(page),
      size: '20',
      sort: 'updatedAt,desc',
    });

    if (status) params.set('status', status);

    return apiRequest(`/teacher/question-groups?${params}`);
  },
};
