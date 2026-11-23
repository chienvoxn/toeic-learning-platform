import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { RemoteAssessmentRepository } from '../src/features/assessment/repositories/assessment.api.js';
import { toServerAttempt, toServerResult, attachAttemptMedia, attachResultMedia } from '../src/features/assessment/api/server-attempt.mapper.js';
import { AnswerSaveQueue } from '../src/features/assessment/answer-save-queue.js';
import { remainingSeconds } from '../src/features/assessment/attempt-time.js';

const snapshot = () => ({ attemptId: 'attempt-pinned', sourceType: 'SECTION_SNAPSHOT', status: 'IN_PROGRESS',
  startedAt: '2026-11-11T00:00:00Z', expiresAt: '2026-11-12T00:00:00Z', expired: false, totalQuestions: 1,
  section: { type: 'READING', durationMinutes: 20, questionCount: 1 }, answers: [],
  questions: [{ order: 1, questionId: 'question', questionVersionId: 'version-pinned', partNumber: 5, questionText: 'Prompt',
    choices: [{ choiceId: 'choice-pinned-a', label: 'A', content: 'First' }, { choiceId: 'choice-pinned-b', label: 'B', content: 'Second' }],
    isCorrect: true, explanation: 'must not enter session view', correctChoiceId: 'choice-pinned-a',
    assets: [{ resourceId: 'media', storageUrl: 'must-not-enter-view' }] }] });
let calls;
test('timer never signals expiry during the last positive fraction of a server deadline', () => {
  assert.equal(remainingSeconds(2000, 1001), 1);
  assert.equal(remainingSeconds(2000, 1999), 1);
  assert.equal(remainingSeconds(2000, 2000), 0);
  assert.equal(remainingSeconds(2000, 2001), 0);
});
beforeEach(() => {
  calls = [];
  globalThis.localStorage = { getItem: () => null, setItem() {}, removeItem() {} };
  globalThis.window = new EventTarget();
  globalThis.fetch = async (url, options) => { calls.push({ url, ...options }); return new Response(JSON.stringify(snapshot())); };
});
test('published full-test catalog and start use the selected version instead of a hard-coded ID', async () => {
  const catalog = [{ testVersionId: 'published-version', title: 'Published test' }];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, ...options });
    return new Response(JSON.stringify(url.endsWith('/full-tests') ? catalog : snapshot()));
  };

  const repo = new RemoteAssessmentRepository();
  assert.deepEqual(await repo.listFullTests(), catalog);
  await repo.startFullTest('published-version');

  assert.equal(calls[0].url, '/api/v1/full-tests');
  assert.equal(calls[1].url, '/api/v1/full-tests/published-version/attempts');
  assert.equal(calls[1].method, 'POST');
});
test('runtime creation sends camelCase source-specific DTOs and no identity fields', async () => {
  const repo = new RemoteAssessmentRepository();
  await repo.startPractice({ partNumbers: [5], questionCount: 4, userId: 'ignored' });
  assert.deepEqual(JSON.parse(calls[0].body), { sourceType: 'PRACTICE_SNAPSHOT', practice: { partNumbers: [5], questionCount: 4 } });
  await repo.startSection({ type: 'READING', questionCount: 8, durationMinutes: 20 });
  assert.deepEqual(JSON.parse(calls[1].body), { sourceType: 'SECTION_SNAPSHOT', section: { type: 'READING', questionCount: 8, durationMinutes: 20 } });
  assert.ok(calls.every(call => call.url === '/api/v1/attempts'));
});
test('safe view uses pinned IDs/server deadline and cannot render a key, explanation or media path', () => {
  const dto = snapshot(); dto.answers = [{ questionVersionId: 'version-pinned', selectedChoiceId: 'choice-pinned-b' }];
  const view = toServerAttempt(dto), question = view.parts[0].questions[0];
  assert.equal(question.id, 'version-pinned'); assert.equal(question.userAnswer, 'B');
  assert.equal(view.expiresAt, Date.parse(dto.expiresAt));
  assert.equal(Object.hasOwn(question, 'correctOption'), false);
  assert.equal(Object.hasOwn(question, 'explanation'), false);
  assert.equal(JSON.stringify(view).includes('must-not-enter-view'), false);
  assert.throws(() => toServerAttempt({ ...dto, expiresAt: null }), /không hợp lệ/);
});
test('save/clear address the pinned version and send only selectedChoiceId', async () => {
  const repo = new RemoteAssessmentRepository();
  await repo.saveAnswer({ attemptId: 'a', questionId: 'pinned-v', selectedChoiceId: 'pinned-c', clientSeq: 9 });
  await repo.saveAnswer({ attemptId: 'a', questionId: 'pinned-v', selectedChoiceId: null });
  assert.equal(calls[0].url, '/api/v1/attempts/a/answers/pinned-v');
  assert.deepEqual(JSON.parse(calls[0].body), { selectedChoiceId: 'pinned-c' });
  assert.deepEqual(JSON.parse(calls[1].body), { selectedChoiceId: null });
});
test('submit never sends local answers and API failure cannot fall back to a local result', async () => {
  const repo = new RemoteAssessmentRepository();
  await repo.submitAttempt('a');
  assert.equal(calls[0].url, '/api/v1/attempts/a/submit'); assert.equal(calls[0].body, undefined);
  globalThis.fetch = async () => new Response(JSON.stringify({ code: 'FORBIDDEN' }), { status: 403 });
  await assert.rejects(repo.getAttempt('a'), { status: 403 });
});
test('result keeps server grading/counts and joins only the pinned prompt', () => {
  const session = toServerAttempt(snapshot());
  const dto = { attemptId: session.id, status: 'GRADED', totalQuestions: 1, correctCount: 0, incorrectCount: 1,
    unansweredCount: 0, rawScore: 0, accuracy: 0, parts: [{ partNumber: 5, correctCount: 0, totalQuestions: 1 }],
    questions: [{ questionVersionId: 'version-pinned', selectedChoiceId: 'choice-pinned-a', correctChoiceId: 'choice-pinned-a', isCorrect: false, explanation: 'Server explanation' }] };
  const result = toServerResult(dto, session);
  assert.equal(result.rawCorrect, 0); assert.equal(result.rawScore, 0);
  assert.equal(result.questions[0].isCorrect, false, 'adapter must not grade by comparing labels');
  assert.equal(result.questions[0].explanation, 'Server explanation');
  assert.deepEqual(result.partResults, dto.parts); assert.equal(result.totalScore, undefined);
  assert.throws(() => toServerResult({ ...dto, status: 'IN_PROGRESS' }, session));
});
test('rapid replacement and clearing are serialized; an old acknowledgement cannot mark a newer choice saved', async () => {
  const started = [], release = [];
  const queue = new AnswerSaveQueue(payload => new Promise(resolve => { started.push(payload.selectedChoiceId); release.push(resolve); }));
  const first = queue.enqueue({ questionId: 'v', selectedChoiceId: 'a' });
  const second = queue.enqueue({ questionId: 'v', selectedChoiceId: 'b' });
  const third = queue.enqueue({ questionId: 'v', selectedChoiceId: null });
  await Promise.resolve(); assert.deepEqual(started, ['a']);
  release[0](); await first; await Promise.resolve(); assert.equal(queue.status, 'saving'); assert.deepEqual(started, ['a','b']);
  release[1](); await second; await Promise.resolve(); assert.deepEqual(started, ['a','b',null]);
  release[2](); await third; await queue.flush(); assert.equal(queue.status, 'saved');
});
test('failed/expired saves remain unsaved and block submit until an actual retry succeeds', async () => {
  let fail = true;
  const queue = new AnswerSaveQueue(async () => { if (fail) throw Object.assign(new Error('Expired'), { code: 'ATTEMPT_EXPIRED' }); });
  await queue.enqueue({ questionId: 'v', selectedChoiceId: 'a' });
  assert.equal(queue.status, 'error'); assert.equal(queue.pending.size, 1);
  await assert.rejects(queue.flush(), /chưa lưu/);
  fail = false; await queue.retry(); await queue.flush(); assert.equal(queue.status, 'saved');
});

test('Listening Part 1-4 media mapper attaches pre-signed audio/image URLs and hides transcript until GRADED', async () => {
  const listeningSnapshot = {
    attemptId: 'attempt-listening-1',
    sourceType: 'SECTION_SNAPSHOT',
    status: 'IN_PROGRESS',
    startedAt: '2026-11-21T02:00:00Z',
    expiresAt: '2026-11-21T02:45:00Z',
    expired: false,
    totalQuestions: 2,
    section: { type: 'LISTENING', durationMinutes: 45, questionCount: 2 },
    answers: [],
    questions: [
      {
        order: 1,
        questionId: 'q-p1',
        questionVersionId: 'qv-p1',
        partNumber: 1,
        questionText: 'Look at the picture.',
        choices: [{ choiceId: 'c1', label: 'A', content: 'A' }, { choiceId: 'c2', label: 'B', content: 'B' }],
        assets: [{ resourceId: 'res-img-1', resourceType: 'IMAGE' }, { resourceId: 'res-aud-1', resourceType: 'AUDIO' }],
      },
      {
        order: 2,
        questionId: 'q-p3',
        questionVersionId: 'qv-p3',
        partNumber: 3,
        questionText: 'Where does the conversation take place?',
        choices: [{ choiceId: 'c3', label: 'A', content: 'A' }, { choiceId: 'c4', label: 'B', content: 'B' }],
        assets: [],
        passage: {
          groupVersionId: 'gv-p3',
          instruction: 'Listen to the conversation.',
          passageText: 'Schedule table',
          assets: [{ resourceId: 'res-aud-3', resourceType: 'AUDIO' }],
        },
      },
    ],
  };

  const mediaInProgress = {
    attemptId: 'attempt-listening-1',
    sourceType: 'SECTION_SNAPSHOT',
    status: 'IN_PROGRESS',
    questions: [
      {
        order: 1,
        questionVersionId: 'qv-p1',
        partNumber: 1,
        assets: [
          { resourceId: 'res-img-1', resourceType: 'IMAGE', mimeType: 'image/svg+xml', url: 'http://localhost:9000/toeic-media/image/p1.svg?X-Amz-Signature=1' },
          { resourceId: 'res-aud-1', resourceType: 'AUDIO', mimeType: 'audio/wav', durationSeconds: 4, url: 'http://localhost:9000/toeic-media/audio/p1.wav?X-Amz-Signature=2', transcript: 'SHOULD_NOT_SHOW_WHILE_IN_PROGRESS' },
        ],
        passageAssets: [],
      },
      {
        order: 2,
        questionVersionId: 'qv-p3',
        partNumber: 3,
        groupVersionId: 'gv-p3',
        assets: [],
        passageAssets: [
          { resourceId: 'res-aud-3', resourceType: 'AUDIO', mimeType: 'audio/wav', durationSeconds: 6, url: 'http://localhost:9000/toeic-media/audio/p3.wav?X-Amz-Signature=3', transcript: 'SHOULD_NOT_SHOW_PASSAGE_TRANSCRIPT' },
        ],
      },
    ],
  };

  const baseAttempt = toServerAttempt(listeningSnapshot);
  const enrichedAttempt = attachAttemptMedia(baseAttempt, mediaInProgress);
  assert.equal(enrichedAttempt.parts[0].questions[0].imageAssets.length, 1);
  assert.equal(enrichedAttempt.parts[0].questions[0].audioAssets.length, 1);
  assert.equal(enrichedAttempt.parts[1].questions[0].passage.audioAssets.length, 1);
  assert.equal(JSON.stringify(enrichedAttempt).includes('SHOULD_NOT_SHOW'), false);

  const gradedDto = {
    attemptId: 'attempt-listening-1',
    status: 'GRADED',
    totalQuestions: 2,
    correctCount: 2,
    incorrectCount: 0,
    unansweredCount: 0,
    rawScore: 2,
    accuracy: 100,
    questions: [
      { questionVersionId: 'qv-p1', selectedChoiceId: 'c1', correctChoiceId: 'c1', isCorrect: true, explanation: 'Exp 1' },
      { questionVersionId: 'qv-p3', selectedChoiceId: 'c3', correctChoiceId: 'c3', isCorrect: true, explanation: 'Exp 3' },
    ],
  };
  const mediaGraded = {
    ...mediaInProgress,
    status: 'GRADED',
    questions: [
      {
        ...mediaInProgress.questions[0],
        assets: [
          mediaInProgress.questions[0].assets[0],
          { ...mediaInProgress.questions[0].assets[1], transcript: '(A) A presenter is pointing at a chart.' },
        ],
      },
      {
        ...mediaInProgress.questions[1],
        passageAssets: [
          { ...mediaInProgress.questions[1].passageAssets[0], transcript: 'Woman: Room B projector is broken.' },
        ],
      },
    ],
  };

  const baseResult = toServerResult(gradedDto, baseAttempt);
  const enrichedResult = attachResultMedia(baseResult, mediaGraded);
  assert.equal(enrichedResult.questions[0].transcript, '(A) A presenter is pointing at a chart.');
  assert.equal(enrichedResult.questions[1].passageTranscript, 'Woman: Room B projector is broken.');
});
