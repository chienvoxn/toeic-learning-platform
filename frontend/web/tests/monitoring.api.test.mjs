import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { monitoringApi } from '../src/features/admin/monitoring.api.js';
import { toClientUser } from '../src/features/auth/api/auth.mapper.js';
let calls;
beforeEach(() => {
  calls = []; globalThis.localStorage = { getItem: () => null, removeItem() {} }; globalThis.window = new EventTarget();
  globalThis.fetch = async (url, options) => { calls.push({ url, ...options }); return new Response(JSON.stringify({ items: [], totalElements: 0 })); };
});
test('audit API encodes exact supported filters and only performs GET', async () => {
  await monitoringApi.audit({ actor: 'actor/id', action: 'ROLE_CREATED', entityType: 'ROLE', entityId: 42, from: '2026-11-13T00:00:00+07:00', page: 2, size: 20, sort: 'action,asc', password: 'ignored' });
  const url = new URL(calls[0].url, 'http://local.test');
  assert.equal(url.pathname, '/api/v1/admin/audit-logs'); assert.equal(url.searchParams.get('actor'), 'actor/id');
  assert.equal(url.searchParams.get('from'), '2026-11-13T00:00:00+07:00'); assert.equal(url.searchParams.get('page'), '2');
  assert.equal(url.searchParams.has('password'), false); assert.equal(calls[0].method, undefined);
  await monitoringApi.detail('audit/id'); assert.equal(calls[1].url, '/api/v1/admin/audit-logs/audit%2Fid');
});
test('health has no user-controlled probe URL and usage only sends the period', async () => {
  await monitoringApi.health({ url: 'https://injected.test' }); assert.equal(calls[0].url, '/api/v1/admin/health');
  await monitoringApi.usage({ from: '', to: '2026-11-14T00:00:00Z', role: 'ignored' });
  assert.equal(calls[1].url, '/api/v1/admin/usage-statistics?to=2026-11-14T00%3A00%3A00Z');
});
test('monitoring errors do not invent audit, health UP or usage counts', async () => {
  for (const status of [401, 403, 503]) {
    globalThis.fetch = async () => new Response(JSON.stringify({ code: 'UNAVAILABLE', message: 'Unavailable' }), { status });
    for (const load of [monitoringApi.audit, monitoringApi.health, monitoringApi.usage]) await assert.rejects(load(), error => error.status === status);
  }
  globalThis.fetch = async () => { throw new TypeError('offline'); };
  await assert.rejects(monitoringApi.usage(), error => error.code === 'NETWORK_ERROR');
});
test('monitoring capabilities require server booleans rather than role inference', () => {
  assert.equal(toClientUser({ roles: ['ADMIN'] }).adminCapabilities, undefined);
  const value = toClientUser({ adminCapabilities: { auditRead: true, healthRead: 'true', usageRead: true } });
  assert.deepEqual(value.adminCapabilities, { userManagement: false, rbacManagement: false, auditRead: true, healthRead: false, usageRead: true, settingsManagement: false });
});
