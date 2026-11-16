import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { contentManagementApi as api } from '../src/features/admin/content-management.api.js';
import { toClientUser } from '../src/features/auth/api/auth.mapper.js';
let calls;
beforeEach(() => {
  calls = []; globalThis.localStorage = { getItem: () => null, removeItem() {} }; globalThis.window = new EventTarget();
  globalThis.fetch = async (url, options) => { calls.push({ url, ...options }); return options.method === 'DELETE' ? new Response(null, { status: 204 }) : new Response(JSON.stringify({ content: [], totalElements: 0 })); };
});
test('content management reuses authoring routes and archive without a body or workflow mutation', async () => {
  await api.list('contents', { kind: 'questions', status: 'PUBLISHED', page: 2, size: 20 });
  assert.equal(calls[0].url, '/api/v1/teacher/contents?status=PUBLISHED&page=2&size=20');
  await api.detail('questions', 'id/slash'); assert.equal(calls[1].url, '/api/v1/teacher/questions/id%2Fslash');
  await api.archive('groups', 'group-id'); assert.equal(calls[2].url, '/api/v1/teacher/question-groups/group-id');
  assert.equal(calls[2].method, 'DELETE'); assert.equal(calls[2].body, undefined);
  assert.throws(() => api.list('tests'), TypeError); assert.equal(calls.length, 3);
});
test('management capabilities need exact server booleans, not role names', () => {
  assert.equal(toClientUser({ roles: ['ADMIN'] }).adminCapabilities, undefined);
  const caps = toClientUser({ adminCapabilities: { contentManagement: true, questionManagement: 'true' } }).adminCapabilities;
  assert.equal(caps.contentManagement, true); assert.equal(caps.questionManagement, false);
});
test('create/update/options use existing resources and explicit partial payloads', async () => {
  await api.create('contents', { title: 'Draft', body: '', categoryCode: 'GRAMMAR' });
  assert.equal(calls[0].method, 'POST'); assert.equal(calls[0].url, '/api/v1/teacher/contents');
  assert.deepEqual(JSON.parse(calls[0].body), { title: 'Draft', body: '', categoryCode: 'GRAMMAR' });
  await api.update('questions', 'id', { explanation: null });
  assert.equal(calls[1].method, 'PATCH'); assert.deepEqual(JSON.parse(calls[1].body), { explanation: null });
  await api.options(); assert.equal(calls[2].url, '/api/v1/teacher/question-options');
});
test('permission, archive conflicts and transport errors never invent list/detail or success', async () => {
  for (const status of [401, 403, 404, 409, 503]) {
    globalThis.fetch = async () => new Response(JSON.stringify({ code: 'DENIED', message: 'Denied' }), { status });
    await assert.rejects(api.archive('contents', 'id'), error => error.status === status);
  }
  globalThis.fetch = async () => { throw new TypeError('offline'); };
  await assert.rejects(api.list('questions'), error => error.code === 'NETWORK_ERROR');
});
