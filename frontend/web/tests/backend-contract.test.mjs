import { test, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { loadBrowserModule } from './load-browser-module.mjs';

const { RemoteAuthRepository } = await loadBrowserModule('features/auth/repositories/auth.api.js');
const { RemoteVocabularyRepository } = await loadBrowserModule('features/vocabulary/repositories/vocabulary.api.js');
const { RemoteContentRepository } = await loadBrowserModule('features/content/repositories/content.api.js');
const { apiRequest, healthApi } = await loadBrowserModule('services/apiClient.js');
const originalFetch = globalThis.fetch;
let requests, queue, storage;
const user = { userId: 'user-1', email: 'learner@example.com', fullName: 'Học viên', roles: ['LEARNER'], targetScore: null };
beforeEach(() => {
  requests = []; queue = []; storage = new Map();
  globalThis.localStorage = { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, String(value)), removeItem: key => storage.delete(key) };
  globalThis.window = new EventTarget();
  globalThis.fetch = async (url, options) => {
    requests.push({ url, ...options });
    assert.ok(queue.length, `Unexpected request: ${url}`);
    const { body, status = 200 } = queue.shift();
    return new Response(status === 204 ? null : JSON.stringify(body), { status });
  };
});
afterEach(() => {
  globalThis.fetch = originalFetch;
  delete globalThis.localStorage; delete globalThis.window;
});
function reply(body, status = 200) { queue.push({ body, status }); }
function bodyAt(index) { return JSON.parse(requests[index].body); }

test('register -> verify -> login -> reload -> logout matches the backend identity contract', async () => {
  const repo = new RemoteAuthRepository();
  const password = randomUUID();
  const token = randomUUID();
  reply({ userId: user.userId, email: user.email, status: 'PENDING' }, 201);
  const registered = await repo.register({ email: user.email, fullName: user.fullName, password });
  assert.equal(registered.status, 'PENDING');
  assert.equal(storage.size, 0, 'registration must not create a session');
  assert.deepEqual(bodyAt(0), { email: user.email, fullName: user.fullName, password });
  assert.equal(requests[0].url, '/api/v1/auth/register');
  reply({ message: 'verified' });
  await repo.verifyEmail(user.email, '123456');
  assert.deepEqual(bodyAt(1), { email: user.email, otp: '123456' });
  reply({ accessToken: token, expiresIn: 900, user });
  const result = await repo.login({ email: user.email, password });
  assert.equal(result.user.id, user.userId);
  assert.equal(result.user.targetScore, null);
  assert.equal(result.user.streakDays, 0);
  assert.equal(storage.get('auth_token'), token);
  reply(user);
  assert.equal((await new RemoteAuthRepository().getCurrentUser()).id, user.userId);
  assert.equal(requests[3].headers.Authorization, `Bearer ${token}`);
  reply(null, 204);
  await repo.logout();
  assert.equal(storage.size, 0);
  assert.equal(await repo.getCurrentUser(), null);
  assert.equal(requests.length, 5, 'anonymous startup must not call private me');
});
test('public login and resend OTP never attach a stale bearer', async () => {
  storage.set('auth_token', randomUUID());
  const repo = new RemoteAuthRepository();
  reply({ accessToken: randomUUID(), user });
  await repo.login({ email: user.email, password: randomUUID() });
  assert.equal(requests[0].headers.Authorization, undefined);
  reply({ message: 'sent' });
  await repo.resendOtp(user.email);
  assert.equal(requests[1].url, '/api/v1/auth/resend-otp');
  assert.equal(requests[1].headers.Authorization, undefined);
});
test('401 clears the expired session; 403 keeps it', async () => {
  const token = randomUUID(); storage.set('auth_token', token);
  let expired = 0; window.addEventListener('auth:expired', () => expired++);
  reply({ code: 'FORBIDDEN', message: 'Forbidden' }, 403);
  await assert.rejects(apiRequest('/users/me'), error => error.status === 403);
  assert.equal(storage.get('auth_token'), token);
  reply({ code: 'TOKEN_EXPIRED', message: 'Expired' }, 401);
  await assert.rejects(apiRequest('/users/me'), error => error.code === 'TOKEN_EXPIRED');
  assert.equal(storage.size, 0); assert.equal(expired, 1);
});
test('a late 401 does not clear a newer login session', async () => {
  const newToken = randomUUID();
  storage.set('auth_token', randomUUID());
  globalThis.fetch = async () => {
    storage.set('auth_token', newToken);
    return new Response(JSON.stringify({ code: 'INVALID_TOKEN' }), { status: 401 });
  };
  await assert.rejects(apiRequest('/users/me'));
  assert.equal(storage.get('auth_token'), newToken);
});
test('profile update uses camelCase and preserves an explicit null target', async () => {
  reply({ ...user, fullName: 'Tên mới' });
  const updated = await new RemoteAuthRepository().updateProfile({ fullName: 'Tên mới', targetScore: null });
  assert.deepEqual(bodyAt(0), { fullName: 'Tên mới', targetScore: null });
  assert.equal(updated.fullName, 'Tên mới');
});

test('a late successful me response cannot restore a logged-out user', async () => {
  storage.set('auth_token', randomUUID());
  globalThis.fetch = async () => {
    storage.delete('auth_token');
    return new Response(JSON.stringify(user));
  };
  assert.equal(await new RemoteAuthRepository().getCurrentUser(), null);
  assert.equal(storage.has('auth_user'), false);
});
test('password change uses the real endpoint and removes the local session', async () => {
  storage.set('auth_token', randomUUID()); reply(null, 204);
  const current = randomUUID(), next = randomUUID();
  await new RemoteAuthRepository().changePassword(current, next);
  assert.equal(requests[0].url, '/api/v1/users/me/password');
  assert.deepEqual(bodyAt(0), { currentPassword: current, newPassword: next });
  assert.equal(storage.size, 0);
});
test('health uses unversioned endpoints without a bearer', async () => {
  storage.set('auth_token', randomUUID()); reply({ status: 'UP' }); reply({ status: 'UP' });
  await healthApi.check(); await healthApi.database();
  assert.deepEqual(requests.map(request => request.url), ['/api/health', '/api/health/database']);
  assert.ok(requests.every(request => !request.headers.Authorization));
});
test('flashcards map the actual DTO; an unreviewed word has no review-state yet', async () => {
  reply([{ vocabularyId: 'v-1', word: 'learn', meaning: 'học', pronunciation: '/lɜːn/', example: 'I learn English.' }]);
  reply({ message: 'Review state not found' }, 404);
  const cards = await new RemoteVocabularyRepository().getDeckCards('d-1');
  assert.equal(requests[0].url, '/api/v1/vocabulary-decks/d-1/flashcards');
  assert.equal(cards[0].id, 'v-1'); assert.equal(cards[0].definitionVi, 'học');
  assert.equal(cards[0].status, 'new');
});
test('all four review ratings map to the implemented integer contract', async () => {
  const repo = new RemoteVocabularyRepository();
  for (const [index, rating] of ['AGAIN', 'HARD', 'GOOD', 'EASY'].entries()) {
    reply({ nextReviewAt: '2026-11-11T00:00:00Z' });
    await repo.rateCard('v-1', rating);
    assert.equal(requests[index].url, '/api/v1/vocabularies/v-1/reviews');
    assert.deepEqual(bodyAt(index), { rating: index + 1 });
  }
});
test('create deck and vocabulary then link using backend identifiers', async () => {
  const repo = new RemoteVocabularyRepository();
  reply({ deckId: 'd-1', name: 'Work' }, 201); await repo.createDeck('Work', 'Office vocabulary');
  assert.deepEqual(bodyAt(0), { name: 'Work', description: 'Office vocabulary' });
  reply({ vocabularyId: 'v-1' }, 201); await repo.createVocabulary({ word: 'work', meaning: 'làm việc' });
  reply(null, 204); await repo.addToDeck('d-1', 'v-1');
  assert.equal(requests[2].url, '/api/v1/vocabulary-decks/d-1/vocabularies');
  assert.deepEqual(bodyAt(2), { vocabularyId: 'v-1' });
});
test('content detail maps body and metadata without inventing media URLs', async () => {
  reply({ contentId: 'c-1', title: 'Grammar', summary: 'Summary', body: '<script>not executed</script>', category: { name: 'Ngữ pháp' }, media: [{ resourceId: 'm-1' }] });
  const item = await new RemoteContentRepository().getContentById('c-1');
  assert.equal(item.id, 'c-1'); assert.equal(item.category, 'Ngữ pháp');
  assert.equal(item.body, '<script>not executed</script>'); assert.equal(item.mediaUrl, '');
});
test('bookmark toggles use POST/DELETE and history is an explicit POST', async () => {
  const repo = new RemoteContentRepository();
  reply({ contentId: 'c-1' }, 201); assert.equal((await repo.toggleBookmark('c-1', false)).isBookmarked, true);
  reply(null, 204); assert.equal((await repo.toggleBookmark('c-1', true)).isBookmarked, false);
  reply(null, 204); await repo.recordHistory('c-1');
  assert.deepEqual(requests.map(request => request.method), ['POST', 'DELETE', 'POST']);
  assert.equal(requests[2].url, '/api/v1/contents/c-1/history');
});

test('catalog reads later pages and resolves saved items using contentId', async () => {
  const content = id => ({ contentId: id, title: id, category: { name: 'Ngữ pháp' } });
  reply({ content: [content('c-1')], totalPages: 2 });
  reply({ content: [], totalPages: 2 });
  reply({ content: [content('c-2')], totalPages: 2 });
  reply({ content: [{ content: content('c-2') }], totalPages: 2 });
  const items = await new RemoteContentRepository().getContents({ search: 'a & b' });
  assert.deepEqual(items.map(item => [item.id, item.isBookmarked]), [['c-1', false], ['c-2', true]]);
  assert.ok(requests[0].url.includes('q=a+%26+b'));
  assert.ok(requests.some(request => request.url.includes('contents?') && request.url.includes('page=1')));
});

test('failed registration does not establish a session', async () => {
  reply({ code: 'EMAIL_ALREADY_EXISTS', message: 'Email đã tồn tại.' }, 409);
  await assert.rejects(new RemoteAuthRepository().register({ email: user.email, fullName: user.fullName, password: randomUUID() }), error => error.status === 409);
  assert.equal(storage.size, 0);
});

test('failed logout clears local state even if backend is unavailable', async () => {
  storage.set('auth_token', randomUUID());
  globalThis.fetch = async () => { throw new TypeError('Connection failed'); };
  await assert.rejects(new RemoteAuthRepository().logout(), error => error.code === 'NETWORK_ERROR');
  assert.equal(storage.size, 0);
});

test('flashcard permission errors are not treated as new words', async () => {
  reply([{ vocabularyId: 'v-1', word: 'learn', meaning: 'học' }]);
  reply({ code: 'FORBIDDEN', message: 'Forbidden' }, 403);
  await assert.rejects(new RemoteVocabularyRepository().getDeckCards('d-1'), error => error.status === 403);
});
