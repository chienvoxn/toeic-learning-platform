// Contract tests use a transport stub; real integration is tested separately by auth.remote.spec.js.
import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { RemoteAuthRepository } from '../src/features/auth/repositories/auth.api.js';
import { toClientUser } from '../src/features/auth/api/auth.mapper.js';
import { readSession, saveSession } from '../src/features/auth/session.js';
import { authErrorMessage, passwordError } from '../src/features/auth/errors.js';
import { apiRequest, healthApi } from '../src/services/apiClient.js';

const repo = new RemoteAuthRepository();
const user = { userId: 'test-user', fullName: 'Learner', email: 'test@example.com', roles: ['LEARNER'], targetScore: null };
const calls = [];
function respond(body, status = 200) {
  globalThis.fetch = async (url, options) => { calls.push({ url, ...options }); return new Response(status === 204 ? null : JSON.stringify(body), { status }); };
}
beforeEach(() => {
  const storage = new Map();
  globalThis.localStorage = { getItem: key => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, String(value)), removeItem: key => storage.delete(key) };
  globalThis.window = new EventTarget();
  calls.length = 0;
});

test('register sends camelCase allowlist and never starts a session', async () => {
  respond({ email: user.email, status: 'ACTIVE' }, 201);
  await repo.register({ email: user.email, password: 'fixture-only', fullName: user.fullName, role: 'ADMIN', targetScore: 750 });
  assert.deepEqual(Object.keys(JSON.parse(calls[0].body)).sort(), ['email', 'fullName', 'password']);
  assert.equal(calls[0].headers.Authorization, undefined);
  assert.equal(readSession(), null);
});

test('login uses Bearer metadata and obtains authoritative me, never cached demo user', async () => {
  localStorage.setItem('auth_user', '{"fullName":"fake"}');
  let count = 0;
  globalThis.fetch = async (url, options) => {
    calls.push({ url, ...options });
    return new Response(JSON.stringify(++count === 1 ? { accessToken: 'contract-token', tokenType: 'Bearer', expiresIn: 900 } : user));
  };
  const result = await repo.login({ email: user.email, password: 'fixture-only' });
  assert.equal(result.user.userId, user.userId);
  assert.equal(calls[1].url, '/api/v1/users/me');
  assert.equal(calls[1].headers.Authorization, 'Bearer contract-token');
  assert.equal(localStorage.getItem('auth_user'), null);
});

test('PATCH preserves omitted, explicit null and sends no system fields', async () => {
  respond(user);
  await repo.updateProfile({ fullName: 'Changed', role: 'ADMIN' });
  assert.deepEqual(JSON.parse(calls[0].body), { fullName: 'Changed' });
  await repo.updateProfile({ targetScore: null });
  assert.deepEqual(JSON.parse(calls[1].body), { targetScore: null });
});

test('CURRENT_PASSWORD_INVALID preserves session; successful 204 removes it', async () => {
  saveSession({ accessToken: 'session', tokenType: 'Bearer', expiresIn: 900 });
  respond({ code: 'CURRENT_PASSWORD_INVALID' }, 422);
  await assert.rejects(repo.changePassword({ currentPassword: 'wrong', newPassword: 'fixture-only' }), { code: 'CURRENT_PASSWORD_INVALID' });
  assert.ok(readSession());
  assert.match(authErrorMessage({ code: 'CURRENT_PASSWORD_INVALID' }), /vẫn được giữ/);
  respond(null, 204);
  await repo.changePassword({ currentPassword: 'old', newPassword: 'fixture-only' });
  assert.equal(readSession(), null);
});

test('invalid session clears current token; stale 401 cannot erase a newer login', async () => {
  saveSession({ accessToken: 'new', tokenType: 'Bearer', expiresIn: 900 });
  respond({ code: 'INVALID_TOKEN' }, 401);
  await assert.rejects(apiRequest('/users/me', { token: 'old' }));
  assert.equal(readSession().accessToken, 'new');
  await assert.rejects(apiRequest('/users/me'));
  assert.equal(readSession(), null);
});

test('invalid login and forbidden me do not masquerade as expired session', async () => {
  saveSession({ accessToken: 'session', tokenType: 'Bearer', expiresIn: 900 });
  respond({ code: 'INVALID_CREDENTIALS' }, 401);
  await assert.rejects(repo.login({ email: user.email, password: 'wrong' }));
  assert.ok(readSession());
  respond({ code: 'FORBIDDEN' }, 403);
  await assert.rejects(repo.getCurrentUser());
  assert.ok(readSession());
});

test('remote mapper has no invented goal, streak, XP or role', () => {
  const mapped = toClientUser(user);
  assert.equal(mapped.targetScore, null);
  assert.equal(mapped.streakDays, undefined);
  assert.equal(mapped.xp, undefined);
  assert.deepEqual(toClientUser({ userId: 'u' }).roles, []);
});

test('password validation enforces UTF-8 byte limit without stripping', () => {
  assert.equal(passwordError('á'.repeat(36)), '');
  assert.notEqual(passwordError('á'.repeat(37)), '');
  assert.notEqual(passwordError(' '.repeat(8)), '');
  assert.equal(passwordError(' secret '), '');
});

test('password recovery endpoints are anonymous; cooldown remains distinguishable', async () => {
  saveSession({ accessToken: 'session', tokenType: 'Bearer', expiresIn: 900 });
  respond({ message: 'ok' });
  await repo.resetPassword(user.email, '123456', 'fixture-only');
  assert.deepEqual(JSON.parse(calls[0].body), { email: user.email, otp: '123456', newPassword: 'fixture-only' });
  assert.equal(calls[0].headers.Authorization, undefined);
  respond({ code: 'OTP_RESEND_TOO_SOON' }, 429);
  await assert.rejects(repo.forgotPassword(user.email), { status: 429, code: 'OTP_RESEND_TOO_SOON' });
});

test('network error never creates a fake user; logout still clears local session', async () => {
  saveSession({ accessToken: 'session', tokenType: 'Bearer', expiresIn: 900 });
  globalThis.fetch = async () => { throw new TypeError('offline'); };
  await assert.rejects(repo.getCurrentUser());
  await assert.rejects(repo.logout());
  assert.equal(readSession(), null);
});

test('health uses unversioned public endpoint, without bearer', async () => {
  respond({ status: 'UP' });
  await healthApi.check();
  assert.equal(calls[0].url, '/api/health');
  assert.equal(calls[0].headers.Authorization, undefined);
});
