import { apiRequest } from '../../services/apiClient';

export const teacherExamsApi = {
  list() {
    return apiRequest('/teacher/tests');
  },

  create({ title, description = '' }) {
    return apiRequest('/teacher/tests', {
      method: 'POST',
      body: {
        title,
        description,
      },
    });
  },
};
