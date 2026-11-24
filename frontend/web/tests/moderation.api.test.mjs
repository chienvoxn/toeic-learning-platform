import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { moderationApi as api } from '../src/features/admin/moderation.api.js';
let calls;
beforeEach(() => {
  calls = []; globalThis.localStorage = { getItem: () => null, removeItem() {} }; globalThis.window = new EventTarget();
  globalThis.fetch = async (url, options) => { calls.push({ url, ...options }); return new Response('{}'); };
});
test('moderation uses version-scoped revisions with explicit reason/visibility and separate authoring routes', async () => {
  await api.capabilities(); await api.list({ kind: 'contents', page: 2 });
  assert.equal(calls[0].url, '/api/v1/moderation/capabilities'); assert.equal(calls[1].url, '/api/v1/moderation/contents?page=2');
  await api.transition('contents', 'p', 'v', 'publish', { expectedRevision: 4, visibility: 'PUBLIC' });
  assert.equal(calls[2].url, '/api/v1/moderation/contents/p/versions/v/publish');
  assert.deepEqual(JSON.parse(calls[2].body), { expectedRevision: 4, visibility: 'PUBLIC' });
  await api.transition('questions', 'p', 'v', 'return', { expectedRevision: 5, reason: 'Revise' });
  assert.deepEqual(JSON.parse(calls[3].body), { expectedRevision: 5, reason: 'Revise' });
  assert.throws(() => api.list({ kind: 'tests', page: 0 }), TypeError);
});
test('moderation preserves server permission/stale errors and never returns a mock success offline', async () => {
  for (const status of [403,409,503]) {
    globalThis.fetch = async () => new Response(JSON.stringify({ code: 'MODERATION_ERROR', message: 'Rejected' }), { status });
    await assert.rejects(api.transition('contents', 'p', 'v', 'approve', { expectedRevision: 1 }), error => error.status === status);
  }
  globalThis.fetch = async () => { throw new TypeError('offline'); };
  await assert.rejects(api.list({ kind: 'contents', page: 0 }), error => error.code === 'NETWORK_ERROR');
});
