import { apiRequest } from '../../../services/apiClient.js';
import { toServerAttempt, toServerResult } from '../api/server-attempt.mapper.js';

export class RemoteAssessmentRepository {
  async startPractice({ partNumbers, questionCount }) {
    return toServerAttempt(await apiRequest('/attempts', { method: 'POST',
      body: { sourceType: 'PRACTICE_SNAPSHOT', practice: { partNumbers, questionCount } } }));
  }

  async startSection({ type, questionCount, durationMinutes }) {
    return toServerAttempt(await apiRequest('/attempts', { method: 'POST',
      body: { sourceType: 'SECTION_SNAPSHOT', section: { type, questionCount, durationMinutes } } }));
  }

  async getAttempt(attemptId) {
    const data = await apiRequest(`/attempts/${attemptId}`);
    return toServerAttempt(data);
  }

  async saveAnswer({ attemptId, questionId, selectedChoiceId }) {
    if (selectedChoiceId === undefined) throw new Error('Thiếu lựa chọn thuộc phiên đã lưu.');
    return apiRequest(`/attempts/${attemptId}/answers/${questionId}`, { method: 'PUT', body: { selectedChoiceId } });
  }

  async submitAttempt(attemptId) {
    return await apiRequest(`/attempts/${attemptId}/submit`, {
      method: 'POST',
    });
  }

  async getResult(attemptId) {
    const [session, result] = await Promise.all([this.getAttempt(attemptId), apiRequest(`/attempts/${attemptId}/result`)]);
    return toServerResult(result, session);
  }
}
