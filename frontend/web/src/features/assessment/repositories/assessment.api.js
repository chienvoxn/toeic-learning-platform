import { apiRequest } from '@/services/apiClient';

export function remoteAttempt(dto) {
  const clockOffset = Date.parse(dto.serverNow) - Date.now();
  const shownGroups = new Set();
  const questions = dto.questions.map(q => {
    if (!q.groupVersionId) return q;
    if (shownGroups.has(q.groupVersionId)) return { ...q, passage: null, instruction: null, assets: (q.assets || []).filter(a => a.scope !== 'GROUP') };
    shownGroups.add(q.groupVersionId); return q;
  });
  const parts = [...new Set(dto.questions.map(q => q.partNumber))].map(partNumber => ({
    partNumber, title: `Part ${partNumber}`, questions: questions.filter(q => q.partNumber === partNumber),
  }));
  return { ...dto, remote: true, parts, expiresAt: Date.parse(dto.expiresAt) - clockOffset,
    listeningEndsAt: dto.listeningEndsAt ? Date.parse(dto.listeningEndsAt) - clockOffset : null,
    clientSequence: Math.max(0, ...dto.questions.map(q => q.clientSequence || 0)) };
}
export class RemoteAssessmentRepository {
  getCatalog(page = 0) { return apiRequest(`/tests?page=${page}`); }
  getTestDetail(id) { return apiRequest(`/tests/${id}`); }
  async startAttempt(testVersionId) {
    const key = `exam_start_${testVersionId}`;
    let startKey = sessionStorage.getItem(key);
    if (!startKey) { startKey = crypto.randomUUID(); sessionStorage.setItem(key, startKey); }
    const dto = await apiRequest('/exam-attempts', { method: 'POST', body: { testVersionId, startKey } });
    sessionStorage.removeItem(key);
    return remoteAttempt(dto);
  }
  async getAttempt(id) { return remoteAttempt(await apiRequest(`/exam-attempts/${id}`)); }
  saveAnswer({ attemptId, questionId, selectedOption, clientSeq }) {
    return apiRequest(`/exam-attempts/${attemptId}/answers`, { method: 'PUT', body: { questionId, selectedOption, clientSeq } });
  }
  submitAttempt(id) { return apiRequest(`/exam-attempts/${id}/submit`, { method: 'POST' }); }
  getResult(id) { return apiRequest(`/exam-attempts/${id}/result`); }
}
