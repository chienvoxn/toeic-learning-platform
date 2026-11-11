import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadBrowserModule } from './load-browser-module.mjs';

const { GuestAssessmentRepository } = await loadBrowserModule('features/assessment/repositories/assessment.guest.js');
function setup() {
  let time = Date.parse('2026-11-11T01:00:00Z'), id = 0;
  const values = new Map();
  const storage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
  const options = { storage, now: () => time, createId: () => String(++id) };
  return { repo: new GuestAssessmentRepository(options), reload: () => new GuestAssessmentRepository(options), advance: milliseconds => { time += milliseconds; }, values };
}
test('anonymous visitor can select parts, answer, resume, submit and review without auth or fetch', async () => {
  const { repo, reload, values } = setup();
  const [test] = await repo.getCatalog();
  const detail = await repo.getTestDetail(test.id);
  assert.equal(test.totalQuestions, detail.availableParts.reduce((sum, part) => sum + part.questionCount, 0));
  const attempt = await repo.startAttempt(test.id, [5]);
  assert.equal(attempt.parts.length, 1);
  assert.equal(attempt.parts[0].partNumber, 5);
  const question = attempt.parts[0].questions[0];
  assert.equal(question.correctOption, undefined, 'do not expose answer keys in the live view');
  await repo.saveAnswer({ attemptId: attempt.id, questionId: question.id, selectedOption: 'A', clientSeq: 1 });
  const resumed = await reload().getAttempt(attempt.id);
  assert.equal(resumed.parts[0].questions[0].userAnswer, 'A');
  assert.equal(resumed.expiresAt, attempt.expiresAt);
  const result = await reload().submitAttempt(attempt.id);
  assert.equal(result.totalQuestions, attempt.parts[0].questions.length);
  assert.equal(result.rawCorrect, 1);
  assert.equal(result.estimatedReadingScore, null, 'no arbitrary TOEIC conversion');
  assert.deepEqual(await reload().getResult(attempt.id), result);
  assert.ok([...values.keys()].every(key => key.startsWith('toeic_guest_attempt_v1_')), 'no auth/user keys created');
});
test('submit is idempotent and graded answers cannot be edited', async () => {
  const { repo, advance } = setup();
  const [test] = await repo.getCatalog(); const attempt = await repo.startAttempt(test.id);
  const result = await repo.submitAttempt(attempt.id);
  advance(10000);
  assert.deepEqual(await repo.submitAttempt(attempt.id), result);
  await assert.rejects(repo.saveAnswer({ attemptId: attempt.id, questionId: attempt.parts[0].questions[0].id, selectedOption: 'A', clientSeq: 1 }), error => error.code === 'ATTEMPT_CLOSED');
});
test('expired attempts are graded on resume without extending the deadline', async () => {
  const { repo, advance, reload } = setup();
  const [test] = await repo.getCatalog(); const attempt = await repo.startAttempt(test.id, [7]);
  advance(attempt.durationSeconds * 1000);
  assert.equal((await reload().getAttempt(attempt.id)).status, 'GRADED');
  assert.equal((await repo.getResult(attempt.id)).rawCorrect, 0);
});
test('expiry rejects a late answer and preserves previous saved answers', async () => {
  const { repo, advance } = setup();
  const [test] = await repo.getCatalog(); const attempt = await repo.startAttempt(test.id, [5]);
  const questionId = attempt.parts[0].questions[0].id;
  await repo.saveAnswer({ attemptId: attempt.id, questionId, selectedOption: 'A', clientSeq: 1 });
  advance(attempt.durationSeconds * 1000);
  await assert.rejects(repo.saveAnswer({ attemptId: attempt.id, questionId, selectedOption: 'B', clientSeq: 2 }), error => error.code === 'ATTEMPT_EXPIRED');
  assert.equal((await repo.getResult(attempt.id)).questions[0].userAnswer, 'A');
});
test('out-of-order saves cannot overwrite a newer answer', async () => {
  const { repo } = setup();
  const [test] = await repo.getCatalog(); const attempt = await repo.startAttempt(test.id);
  const questionId = attempt.parts[0].questions[0].id;
  await repo.saveAnswer({ attemptId: attempt.id, questionId, selectedOption: 'B', clientSeq: 2 });
  await repo.saveAnswer({ attemptId: attempt.id, questionId, selectedOption: 'A', clientSeq: 1 });
  assert.equal((await repo.getAttempt(attempt.id)).parts[0].questions[0].userAnswer, 'B');
  assert.equal((await repo.getAttempt(attempt.id)).clientSequence, 2);
});
test('unknown IDs do not silently create attempts or grade unsubmitted work', async () => {
  const { repo, values } = setup();
  await assert.rejects(repo.getTestDetail('missing'));
  await assert.rejects(repo.getAttempt('missing'));
  await assert.rejects(repo.getResult('missing'));
  assert.equal(values.size, 0);
  const [test] = await repo.getCatalog(); const attempt = await repo.startAttempt(test.id);
  await assert.rejects(repo.getResult(attempt.id), error => error.code === 'ATTEMPT_NOT_SUBMITTED');
  assert.equal((await repo.getAttempt(attempt.id)).status, 'IN_PROGRESS');
});
test('invalid parts and choices are rejected', async () => {
  const { repo } = setup(); const [test] = await repo.getCatalog();
  for (const parts of [[], [1], [5, 5], null]) await assert.rejects(repo.startAttempt(test.id, parts));
  const attempt = await repo.startAttempt(test.id);
  await assert.rejects(repo.saveAnswer({ attemptId: attempt.id, questionId: 'unknown', selectedOption: 'A', clientSeq: 1 }));
  await assert.rejects(repo.saveAnswer({ attemptId: attempt.id, questionId: attempt.parts[0].questions[0].id, selectedOption: 'Z', clientSeq: 1 }));
});
test('blocked storage reports an error instead of pretending an answer was saved', async () => {
  const repo = new GuestAssessmentRepository({ storage: { setItem() { throw new Error('Quota'); } }, now: () => Date.now(), createId: () => '1' });
  const [test] = await repo.getCatalog();
  await assert.rejects(repo.startAttempt(test.id), /Không lưu được/);
});
