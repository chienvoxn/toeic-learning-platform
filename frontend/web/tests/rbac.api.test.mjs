import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { rbacApi } from '../src/features/admin/rbac.api.js';
let calls;
beforeEach(() => {
  calls = []; globalThis.localStorage = { getItem: () => null, removeItem() {} }; globalThis.window = new EventTarget();
  globalThis.fetch = async (url, options) => { calls.push({ url, ...options }); return new Response(JSON.stringify({ items: [] })); };
});
test('RBAC queries encode Unicode, paging and only supported fields', async () => {
  await rbacApi.users({ q: ' Nguyễn & admin ', page: 2, size: 10, role: 'ignored' });
  const url = new URL(calls[0].url, 'http://local.test');
  assert.equal(url.pathname, '/api/v1/admin/rbac/users'); assert.equal(url.searchParams.get('q'), 'Nguyễn & admin');
  assert.equal(url.searchParams.get('page'), '2'); assert.equal(url.searchParams.has('role'), false);
});
test('RBAC mutations send exact allowlisted payloads and escaped identifiers', async () => {
  await rbacApi.create({ name: 'QA_ROLE', displayName: 'QA', description: null, permissionIds: [1] });
  assert.deepEqual(JSON.parse(calls[0].body), { name: 'QA_ROLE', displayName: 'QA', description: null });
  await rbacApi.edit(1, { displayName: 'Admin', description: null });
  assert.equal(Object.hasOwn(JSON.parse(calls[1].body), 'name'), false);
  await rbacApi.setRoles('user/id', [2]); assert.equal(calls[2].url, '/api/v1/admin/users/user%2Fid/roles');
  assert.deepEqual(JSON.parse(calls[2].body), { roleIds: [2] });
  await rbacApi.setPermissions(3, [4]); assert.deepEqual(JSON.parse(calls[3].body), { permissionIds: [4] });
  globalThis.fetch = async (url, options) => { calls.push({ url, ...options }); return new Response(null, { status: 204 }); };
  assert.equal(await rbacApi.remove(3), null); assert.equal(calls[4].method, 'DELETE'); assert.equal(calls[4].body, undefined);
});
test('RBAC permission errors, conflicts and network failures never fall back to mock', async () => {
  for (const status of [403, 409]) {
    globalThis.fetch = async () => new Response(JSON.stringify({ code: 'DENIED', message: 'Denied' }), { status });
    await assert.rejects(rbacApi.roles(), error => error.status === status);
  }
  globalThis.fetch = async () => { throw new TypeError('offline'); };
  await assert.rejects(rbacApi.permissions(), error => error.code === 'NETWORK_ERROR');
});
