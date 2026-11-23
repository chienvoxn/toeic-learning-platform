
import { apiRequest } from '../../services/apiClient';

export const teacherContentApi = {
  list({
    page = 0,
    size = 20,
    status = '',
    categoryCode = '',
  } = {}) {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
      sort: 'updatedAt,desc',
    });

    if (status) params.set('status', status);
    if (categoryCode) {
      params.set('categoryCode', categoryCode);
    }

    return apiRequest(`/teacher/contents?${params}`);
  },

  detail(id) {
    return apiRequest(
      `/teacher/contents/${encodeURIComponent(id)}`
    );
  },

  create(payload) {
    return apiRequest('/teacher/contents', {
      method: 'POST',
      body: payload,
    });
  },

  update(id, payload) {
    return apiRequest(
      `/teacher/contents/${encodeURIComponent(id)}`,
      {
        method: 'PATCH',
        body: payload,
      }
    );
  },

  archive(id) {
    return apiRequest(
      `/teacher/contents/${encodeURIComponent(id)}`,
      {
        method: 'DELETE',
      }
    );
  },
};
