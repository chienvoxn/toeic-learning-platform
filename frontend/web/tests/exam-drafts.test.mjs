import test from 'node:test';
import assert from 'node:assert/strict';
import { documentUnits, toDraftDocument, unitKey, examDraftApi } from '../src/features/assessment/exam-drafts.api.js';

test('round trip keeps group version, business order and strips presentation fields', () => {
  const source = { title: 'Draft', description: null, testType: 'FULL', durationSeconds: null, sections: [
    { type: 'LISTENING', parts: [{ partNumber: 3, questions: [
      { questionVersionId: 'q3', groupVersionId: 'g1' }, { questionVersionId: 'q2', groupVersionId: 'g1' },
    ] }] }, { type: 'READING', parts: [{ partNumber: 5, questions: [{ questionVersionId: 'q4', groupVersionId: null }] }] },
  ] };
  const units = documentUnits(source);
  assert.equal(units.length, 2); assert.equal(unitKey(units[0]), 'g1');
  units[0].questions[0].questionText = 'Presentation only';
  assert.deepEqual(toDraftDocument({ ...source, durationSeconds: '' }, units), source);
});
test('empty drafts do not infer mandatory TOEIC counts or duration', () => {
  assert.deepEqual(toDraftDocument({ title: 'Draft', testType: 'READING', description: '', durationSeconds: '' }, []),
    { title: 'Draft', testType: 'READING', description: null, durationSeconds: null, sections: [] });
});
test('save uses version path and expected revision, never an attempt route or publisher', async () => {
  const before = globalThis.fetch; let request;
  globalThis.fetch = async (url, options) => { request = { url, ...options }; return new Response('{}'); };
  globalThis.localStorage = { getItem: () => null };
  try {
    await examDraftApi.update({ testId: 'test', versionId: 'version', revision: 3 }, { title: 'New' });
    assert.equal(request.url, '/api/v1/teacher/tests/test/versions/version');
    assert.equal(request.method, 'PUT'); assert.deepEqual(JSON.parse(request.body), { revision: 3, document: { title: 'New' } });
  } finally { globalThis.fetch = before; delete globalThis.localStorage; }
});
