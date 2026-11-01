import { apiRequest } from '@/services/apiClient';
import { toClientAttempt, toSaveAnswerDto } from '../api/assessment.mapper';

export class RemoteAssessmentRepository {
  async getCatalog() {
    return await apiRequest('/tests');
  }

  async getTestDetail(testId) {
    return await apiRequest(`/tests/${testId}`);
  }

  async startAttempt(testId, selectedParts) {
    const data = await apiRequest('/attempts', {
      method: 'POST',
      body: {
        test_id: testId,
        selected_parts: selectedParts,
        mode: 'PRACTICE',
      },
    });
    return toClientAttempt(data);
  }

  async getAttempt(attemptId) {
    const data = await apiRequest(`/attempts/${attemptId}`);
    return toClientAttempt(data);
  }

  async saveAnswer({ attemptId, questionId, selectedOption, clientSeq }) {
    const dto = toSaveAnswerDto(attemptId, questionId, selectedOption, clientSeq);
    return await apiRequest(`/attempts/${attemptId}/answers`, {
      method: 'PUT',
      body: dto,
    });
  }

  async submitAttempt(attemptId) {
    return await apiRequest(`/attempts/${attemptId}/submit`, {
      method: 'POST',
    });
  }

  async getResult(attemptId) {
    return await apiRequest(`/attempts/${attemptId}/result`);
  }
}
