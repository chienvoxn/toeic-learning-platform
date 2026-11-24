import test from 'node:test';
import assert from 'node:assert/strict';
import { EXAM_KEY, rememberExam, forgetExam, restrictExam, confirmAiAllowed, examTutorState } from '../src/features/ai/exam-tutor-state.js';
const now = 1000;
const storage = () => { const map = new Map();return { getItem: key => map.get(key) ?? null, setItem: (key, value) => map.set(key, value) }; };
const attempt = (sourceType = 'SECTION_SNAPSHOT', extra = {}) => ({ id: 'exam', remote: true, sourceType, status: 'IN_PROGRESS', expiresAt: 2000, ...extra });
test('Practice and Guest never create an exam restriction, including old unscoped markers', () => {
  const s = storage();s.setItem(EXAM_KEY, 'server-enforced');
  rememberExam(attempt('PRACTICE_SNAPSHOT'), 'one', s, now);assert.equal(examTutorState('one', s, now).active, false);
  rememberExam(attempt('TEST_VERSION', { remote: false }), 'one', s, now);assert.equal(examTutorState('one', s, now).active, false);
});
test('Section, FullMock and legacy TEST_VERSION hints retain the exact server deadline', () => {
  for (const source of ['SECTION_SNAPSHOT', 'TEST_VERSION']) {
    const s = storage();rememberExam(attempt(source), 'one', s, now);
    assert.equal(examTutorState('one', s, 1999).active, true);assert.equal(examTutorState('one', s, 2000).active, false);
  }
});
test('two tabs retain both exam hints; finishing one cannot unlock another', () => {
  const s = storage();rememberExam(attempt(), 'one', s, now);rememberExam(attempt('TEST_VERSION', { id: 'other' }), 'one', s, now);
  forgetExam('exam', 'one', s);assert.equal(examTutorState('one', s, now).active, true);
  rememberExam(attempt('TEST_VERSION', { id: 'other', status: 'GRADED' }), 'one', s, now);assert.equal(examTutorState('one', s, now).active, false);
});
test('logout/login actor change cannot leak a previous actor restriction', () => {
  const s = storage();rememberExam(attempt(), 'one', s, now);restrictExam('one', 'conversation', s);
  assert.equal(examTutorState('two', s, now).active, false);assert.equal(examTutorState('one', s, now).active, true);
  confirmAiAllowed('two', s);assert.equal(examTutorState('one', s, now).active, true);
});
test('server denial survives reload and expiry until an explicit successful guarded read', () => {
  const s = storage();rememberExam(attempt(), 'one', s, now);restrictExam('one', 'conversation', s);
  assert.deepEqual(examTutorState('one', s, 3000), { active: true, conversationId: 'conversation' });
  forgetExam('exam', 'one', s);assert.equal(examTutorState('one', s, 3000).active, true);
  confirmAiAllowed('one', s);assert.equal(examTutorState('one', s, 3000).active, false);
});
test('bad storage rows or denied writes cannot break assessment submission', () => {
  const s = storage();s.setItem(EXAM_KEY, JSON.stringify({ version: 1, attempts: [null], restrictions: [null] }));
  assert.equal(examTutorState('one', s, now).active, false);
  const denied = { getItem: () => null, setItem() { throw new Error('storage disabled'); } };
  assert.doesNotThrow(() => rememberExam(attempt(), 'one', denied, now));assert.doesNotThrow(() => forgetExam('exam', 'one', denied));
});
