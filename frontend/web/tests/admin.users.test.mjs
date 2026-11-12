import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { adminUsersApi } from '../src/features/admin/admin-users.api.js';

let calls;
beforeEach(() => {
  calls = []; globalThis.localStorage = { getItem: () => null, removeItem() {} }; globalThis.window = new EventTarget();
  globalThis.fetch = async (url, options) => { calls.push({ url, ...options }); return new Response(JSON.stringify({ items: [], totalElements: 0 })); };
});
test('admin search encodes literal Unicode and sends only supported filters', async () => {
  await adminUsersApi.list({ q: ' Nguyễn_% & teacher ', role: 'TEACHER', status: 'LOCKED', page: 2, size: 20, permission: 'ignored' });
  const url = new URL(calls[0].url, 'https://local.test');
  assert.equal(url.pathname, '/api/v1/admin/users'); assert.equal(url.searchParams.get('q'), 'Nguyễn_% & teacher');
  assert.equal(url.searchParams.get('role'), 'TEACHER'); assert.equal(url.searchParams.get('page'), '2');
  assert.equal(url.searchParams.has('permission'), false);
});
test('lock write contains only requested status, never role/permission/session data', async () => {
  await adminUsersApi.changeStatus('actor/id', 'LOCKED', { roles: ['ADMIN'] });
  assert.equal(calls[0].url, '/api/v1/admin/users/actor%2Fid/status'); assert.equal(calls[0].method, 'PATCH');
  assert.deepEqual(JSON.parse(calls[0].body), { status: 'LOCKED' });
});
test('forbidden and offline management calls fail visibly without a fake list or successful lock', async () => {
  globalThis.fetch = async () => new Response(JSON.stringify({ code: 'FORBIDDEN', message: 'Denied' }), { status: 403 });
  await assert.rejects(adminUsersApi.list(), error => error.status === 403 && error.code === 'FORBIDDEN');
  globalThis.fetch = async () => { throw new TypeError('offline'); };
  await assert.rejects(adminUsersApi.changeStatus('id', 'ACTIVE'), error => error.code === 'NETWORK_ERROR');
});
