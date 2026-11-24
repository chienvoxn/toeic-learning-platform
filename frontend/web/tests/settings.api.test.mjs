import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { settingsApi } from '../src/features/admin/settings.api.js';
import { toClientUser } from '../src/features/auth/api/auth.mapper.js';
let calls;
beforeEach(() => {
  calls = []; globalThis.localStorage = { getItem: () => null, removeItem() {} }; globalThis.window = new EventTarget();
  globalThis.fetch = async (url, options) => { calls.push({ url, ...options }); return new Response(JSON.stringify([])); };
});
test('settings writes contain only typed value/revision and escaped key', async () => {
  await settingsApi.update('key/id', 20, 4); assert.equal(calls[0].url, '/api/v1/admin/system-settings/key%2Fid');
  assert.equal(calls[0].method, 'PATCH'); assert.deepEqual(JSON.parse(calls[0].body), { value: 20, expectedRevision: 4 });
  await settingsApi.list(); assert.equal(calls[1].url, '/api/v1/admin/system-settings'); assert.equal(calls[1].body, undefined);
});
test('settings permission uses server boolean and never ADMIN name inference', () => {
  assert.equal(toClientUser({ roles: ['ADMIN'] }).adminCapabilities, undefined);
  assert.equal(toClientUser({ adminCapabilities: { settingsManagement: true } }).adminCapabilities.settingsManagement, true);
  assert.equal(toClientUser({ adminCapabilities: { settingsManagement: 'true' } }).adminCapabilities.settingsManagement, false);
});
test('conflicts, denied writes and connection failures cannot report successful settings', async () => {
  for (const status of [403, 409, 503]) {
    globalThis.fetch = async () => new Response(JSON.stringify({ code: 'DENIED', message: 'Denied' }), { status });
    await assert.rejects(settingsApi.update('key', 2, 1), error => error.status === status);
  }
  globalThis.fetch = async () => { throw new TypeError('offline'); };
  await assert.rejects(settingsApi.list(), error => error.code === 'NETWORK_ERROR');
});
