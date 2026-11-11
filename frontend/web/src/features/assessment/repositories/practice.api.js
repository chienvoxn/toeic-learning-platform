import { apiRequest } from '@/services/apiClient';
export const practiceApi = {
  startSection: (questionCount, durationMinutes) => apiRequest('/attempts', { method: 'POST', body: { sourceType: 'SECTION_SNAPSHOT', section: { type: 'READING', questionCount, durationMinutes } } }),
  start: (questionCount, partNumbers = [5]) => apiRequest('/attempts', { method: 'POST', body: { sourceType: 'PRACTICE_SNAPSHOT', practice: { partNumbers, questionCount } } }),
  get: id => apiRequest(`/attempts/${id}`),
  save: (id, questionVersionId, selectedChoiceId) => apiRequest(`/attempts/${id}/answers/${questionVersionId}`, { method: 'PUT', body: { selectedChoiceId } }),
  submit: id => apiRequest(`/attempts/${id}/submit`, { method: 'POST' }),
  result: id => apiRequest(`/attempts/${id}/result`),
};
