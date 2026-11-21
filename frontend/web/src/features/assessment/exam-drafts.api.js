import { apiRequest } from '../../services/apiClient.js';

const base = '/teacher/tests';
export const examDraftApi = {
  capabilities: (_query, signal) => apiRequest(`${base}/capabilities`, { signal }),
  list: (page = 0, signal) => apiRequest(`${base}?page=${page}`, { signal }),
  read: (id, signal) => apiRequest(`${base}/${encodeURIComponent(id)}`, { signal }),
  units: ({ partNumber, page = 0 }, signal) => apiRequest(`${base}/question-units?partNumber=${partNumber}&page=${page}`, { signal }),
  create: document => apiRequest(base, { method: 'POST', body: document }),
  update: (draft, document) => apiRequest(`${base}/${encodeURIComponent(draft.testId)}/versions/${encodeURIComponent(draft.versionId)}`, {
    method: 'PUT', body: { revision: draft.revision, document },
  }),
};

export const unitKey = unit => unit.questions[0].groupVersionId || unit.questions[0].questionVersionId;
export function documentUnits(document) {
  const result = [];
  for (const section of document.sections) for (const part of section.parts) {
    for (const ref of part.questions) {
      const previous = result.at(-1);
      if (ref.groupVersionId && previous?.questions[0].groupVersionId === ref.groupVersionId) previous.questions.push({ ...ref });
      else result.push({ partNumber: part.partNumber, questions: [{ ...ref }] });
    }
  }
  return result;
}
export function toDraftDocument(editor, units) {
  const sections = [];
  for (const type of ['LISTENING', 'READING']) {
    const parts = [];
    for (let partNumber = type === 'LISTENING' ? 1 : 5; partNumber <= (type === 'LISTENING' ? 4 : 7); partNumber++) {
      const questions = units.filter(unit => unit.partNumber === partNumber).flatMap(unit => unit.questions)
        .map(q => ({ questionVersionId: q.questionVersionId, groupVersionId: q.groupVersionId ?? null }));
      if (questions.length) parts.push({ partNumber, questions });
    }
    if (parts.length) sections.push({ type, parts });
  }
  return { title: editor.title, description: editor.description || null, testType: editor.testType,
    durationSeconds: editor.durationSeconds === '' ? null : Number(editor.durationSeconds), sections };
}
