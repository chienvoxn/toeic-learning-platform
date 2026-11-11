// HTTP + PostgreSQL integration smoke test, ONLY for the isolated local test environment.
// Never use production credentials. Accounts and content remain in this disposable database.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';

const base = 'http://127.0.0.1:8085/api/v1';
const psql = process.env.PSQL_BIN;
if (!psql) throw new Error('Set PSQL_BIN to the installed PostgreSQL psql executable.');
function sql(statement) { return execFileSync(psql, ['-h', '127.0.0.1', '-p', '55439', '-U', 'postgres', '-d', 'toeic_exam_workflow', '-v', 'ON_ERROR_STOP=1', '-At', '-c', statement], { encoding: 'utf8' }).trim(); }
async function request(path, { token, body, method = 'GET', expected = 200 } = {}) {
  const multipart = body instanceof FormData;
  const response = await fetch(base + path, { method, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(!multipart && body !== undefined ? { 'Content-Type': 'application/json' } : {}) }, body: body === undefined ? undefined : multipart ? body : JSON.stringify(body) });
  const text = await response.text();
  // Avoid dumping response bodies: authentication responses contain tokens.
  assert.equal(response.status, expected, `${method} ${path}: expected ${expected}, got ${response.status}; ${response.ok ? '' : JSON.parse(text || '{}').code}`);
  return text ? JSON.parse(text) : null;
}
async function account(role) {
  const email = `exam-smoke-${randomUUID()}@example.invalid`, password = randomUUID() + '!aA';
  const user = await request('/auth/register', { method: 'POST', expected: 201, body: { email, password, fullName: `Exam ${role}` } });
  assert.match(user.userId, /^[a-f0-9-]{36}$/i);
  if (role !== 'LEARNER') sql(`INSERT INTO user_roles(user_id,role_id) SELECT '${user.userId}',role_id FROM roles WHERE name='${role}' ON CONFLICT DO NOTHING`);
  const auth = await request('/auth/login', { method: 'POST', body: { email, password } });
  return auth.accessToken;
}
const teacher = await account('TEACHER'), admin = await account('ADMIN'), learner = await account('LEARNER'), other = await account('LEARNER');
const wave = Buffer.alloc(1644); wave.write('RIFF'); wave.writeUInt32LE(1636, 4); wave.write('WAVEfmt ', 8); wave.writeUInt32LE(16, 16); wave.writeUInt16LE(1, 20); wave.writeUInt16LE(1, 22); wave.writeUInt32LE(8000, 24); wave.writeUInt32LE(16000, 28); wave.writeUInt16LE(2, 32); wave.writeUInt16LE(16, 34); wave.write('data', 36); wave.writeUInt32LE(1600, 40);
async function upload(bytes, type, name) { const body = new FormData(); body.append('file', new Blob([bytes], { type }), name); return request('/teacher/media', { token: teacher, method: 'POST', body, expected: 201 }); }
const audio = await upload(wave, 'audio/wav', 'silence.wav');
const image = await upload(Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a3ioAAAAASUVORK5CYII=', 'base64'), 'image/png', 'pixel.png');
const conversion = await request('/teacher/score-conversions', { token: admin, method: 'POST', expected: 201, body: { name: 'Synthetic smoke-test table — NOT a TOEIC reference', listening: Array(101).fill(5), reading: Array(101).fill(5) } });
const groups = [], questions = [];
const question = (partNumber, groupKey = null) => ({ partNumber, groupKey, questionText: `Synthetic question ${questions.length + 1}`, choices: partNumber === 2 ? ['A', 'B', 'C'] : ['A', 'B', 'C', 'D'], answer: 'A', explanation: 'Smoke test explanation.', audioId: partNumber <= 2 ? audio.resourceId : null, imageId: partNumber === 1 ? image.resourceId : null });
for (let p = 1; p <= 7; p++) {
  if ([1, 2, 5].includes(p)) { for (let n = 0; n < ({ 1: 6, 2: 25, 5: 30 }[p]); n++) questions.push(question(p)); }
  else {
    const sizes = p === 3 ? Array(13).fill(3) : p === 4 ? Array(10).fill(3) : p === 6 ? Array(4).fill(4) : [...Array(9).fill(3), 2, ...Array(5).fill(5)];
    sizes.forEach((size, i) => { const key = `p${p}-${i}`; groups.push({ key, partNumber: p, passageText: p >= 6 ? 'Synthetic passage, no copyrighted exam content.' : null, audioId: p <= 4 ? audio.resourceId : null, transcript: p <= 4 ? 'Synthetic transcript.' : null }); for (let n = 0; n < size; n++) questions.push(question(p, key)); });
  }
}
const doc = { title: 'Full workflow smoke test', testType: 'FULL', durationSeconds: 7200, maxAttempts: 1, conversionId: conversion, groups, questions };
assert.equal(questions.length, 200);
let draft = await request('/teacher/tests/import', { token: teacher, method: 'POST', expected: 201, body: doc });
async function transition(action, token, extra = {}) { draft = await request(`/teacher/tests/${draft.versionId}/${action}`, { token, method: 'POST', body: { revision: draft.revision, ...extra } }); }
await transition('submit-review', teacher);
await request(`/teacher/tests/${draft.versionId}/publish`, { token: teacher, method: 'POST', body: { revision: draft.revision }, expected: 403 });
await transition('review', admin, { decision: 'APPROVE' }); await transition('publish', admin);
const startKey = randomUUID();
const starts = await Promise.all(Array.from({ length: 3 }, () => request('/exam-attempts', { token: learner, method: 'POST', expected: 201, body: { testVersionId: draft.versionId, startKey } })));
assert.equal(new Set(starts.map(a => a.id)).size, 1);
const attempt = starts[0], aid = attempt.id, qid = attempt.questions[0].id;
assert.equal(attempt.questions.length, 200); assert.ok(!JSON.stringify(attempt).includes('correctOption')); assert.ok(!JSON.stringify(attempt).includes('Synthetic transcript'));
await request(`/exam-attempts/${aid}`, { token: other, expected: 404 });
await request('/exam-attempts', { token: learner, method: 'POST', expected: 409, body: { testVersionId: draft.versionId, startKey: randomUUID() } });
const reading = attempt.questions.find(q => q.partNumber === 5);
await request(`/exam-attempts/${aid}/answers`, { token: learner, method: 'PUT', expected: 409, body: { questionId: reading.id, selectedOption: 'A', clientSeq: 1 } });
await request(`/exam-attempts/${aid}/answers`, { token: learner, method: 'PUT', body: { questionId: qid, selectedOption: 'A', clientSeq: 1 } });
const mediaResponse = await fetch(base + `/media/${audio.resourceId}`, { headers: { Authorization: `Bearer ${learner}` } }); assert.equal(mediaResponse.status, 200); assert.equal((await mediaResponse.arrayBuffer()).byteLength, wave.length);
const rangeResponse = await fetch(base + `/media/${audio.resourceId}`, { headers: { Authorization: `Bearer ${learner}`, Range: 'bytes=0-15' } }); assert.equal(rangeResponse.status, 206); assert.equal((await rangeResponse.arrayBuffer()).byteLength, 16);
sql(`UPDATE attempts SET listening_ends_at=now()-interval '1 second' WHERE attempt_id='${aid}'`);
await request(`/exam-attempts/${aid}/answers`, { token: learner, method: 'PUT', expected: 409, body: { questionId: qid, selectedOption: 'B', clientSeq: 2 } });
await request(`/exam-attempts/${aid}/answers`, { token: learner, method: 'PUT', body: { questionId: reading.id, selectedOption: 'A', clientSeq: 2 } });
const results = await Promise.all(Array.from({ length: 3 }, () => request(`/exam-attempts/${aid}/submit`, { token: learner, method: 'POST' })));
assert.deepEqual(results[0], results[1]); assert.deepEqual(results[1], results[2]); assert.equal(results[0].rawCorrect, 2); assert.equal(results[0].estimatedTotalScore, 10);
assert.equal(sql(`SELECT count(*) FROM attempt_part_results WHERE attempt_id='${aid}'`), '7');
assert.equal(sql(`SELECT count(*) FROM outbox_events WHERE aggregate_id='${aid}' AND event_type='AttemptGraded'`), '1');
const concurrentLearner = await account('LEARNER');
const race = await request('/exam-attempts', { token: concurrentLearner, method: 'POST', expected: 201, body: { testVersionId: draft.versionId, startKey: randomUUID() } });
const [saveResponse, submitted] = await Promise.all([
  fetch(base + `/exam-attempts/${race.id}/answers`, { method: 'PUT', headers: { Authorization: `Bearer ${concurrentLearner}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ questionId: race.questions[0].id, selectedOption: 'A', clientSeq: 1 }) }),
  request(`/exam-attempts/${race.id}/submit`, { token: concurrentLearner, method: 'POST' }),
]);
assert.ok([200, 409].includes(saveResponse.status)); assert.equal(submitted.rawCorrect, saveResponse.status === 200 ? 1 : 0);
const expiryLearner = await account('LEARNER');
const expiring = await request('/exam-attempts', { token: expiryLearner, method: 'POST', expected: 201, body: { testVersionId: draft.versionId, startKey: randomUUID() } });
sql(`UPDATE attempts SET expires_at=now()-interval '1 second' WHERE attempt_id='${expiring.id}'`);
const deadline = Date.now() + 45000;
while (sql(`SELECT status FROM attempts WHERE attempt_id='${expiring.id}'`) !== 'GRADED' && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 1000));
assert.equal(sql(`SELECT status FROM attempts WHERE attempt_id='${expiring.id}'`), 'GRADED', 'expiry worker grades without a browser or GET request');
await transition('retire', admin);
assert.deepEqual(await request(`/exam-attempts/${aid}/result`, { token: learner }), results[0]);
console.log('PASS: real HTTP auth, media/range, 200-question import, review/publication RBAC, concurrent start/save/submit, section timing, background expiry, conversion snapshot, results and retirement.');
