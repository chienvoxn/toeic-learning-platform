import { test } from 'node:test';
import assert from 'node:assert/strict';
import { toClientUser } from '../src/features/auth/api/auth.mapper.js';

test('admin UI capabilities use server booleans, never a role-name inference or truthy coercion', () => {
  const without = toClientUser({ userId: 'a', roles: ['ADMIN'] });
  assert.equal(without.adminCapabilities, undefined);
  const response = toClientUser({ userId: 'a', roles: ['ADMIN'], adminCapabilities: { userManagement: false, rbacManagement: true } });
  assert.deepEqual(response.adminCapabilities, { userManagement: false, rbacManagement: true });
  assert.deepEqual(toClientUser({ adminCapabilities: { userManagement: 'true', rbacManagement: 1 } }).adminCapabilities,
    { userManagement: false, rbacManagement: false });
});
