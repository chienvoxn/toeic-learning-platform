import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';

// Opt-in live tests against toeic_learning_demo, never mock API or edit DB history.
const demo = JSON.parse(readFileSync(new URL('../../../backend/target/local-demo-credentials.json', import.meta.url), 'utf8').replace(/^\uFEFF/, ''));
let steps = 0;
async function call(request, method, path, token, data) {
  steps++;
  return request.fetch('/api/v1' + path, { method, ...(token ? { headers: { Authorization: 'Bearer ' + token } } : {}), ...(data === undefined ? {} : { data }) });
}
async function adminSession(request) {
  const response = await call(request, 'POST', '/auth/login', null, { email: demo.adminEmail, password: demo.password });
  expect(response.status()).toBe(200); return (await response.json()).accessToken;
}
async function account(request) {
  const value = { email: 'admin02-' + randomUUID() + '@example.test', password: randomUUID() + 'aA9!', fullName: 'ADMIN02 Live QA' };
  expect((await call(request, 'POST', '/auth/register', null, value)).status()).toBe(201);
  const response = await call(request, 'POST', '/auth/login', null, value); expect(response.status()).toBe(200);
  const session = await response.json(); return { ...value, id: session.user.userId, token: session.accessToken };
}
async function login(page, email = demo.adminEmail, password = demo.password) {
  await page.goto('/login'); await page.locator('#login-email').fill(email); await page.locator('#login-password').waitFor();
  // Avoid putting secret input values in Playwright action logs or assertion snapshots.
  await page.evaluate(value => {
    const input = document.querySelector('#login-password');
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }, password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(); await page.waitForURL(url => url.pathname !== '/login');
}
async function search(page, q) {
  await page.locator('#rbac-search').fill(q); await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Quản lý', exact: true })).toHaveCount(1);
  await page.getByRole('button', { name: 'Quản lý', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Chi tiết RBAC' })).toBeVisible();
}
test.beforeEach(({ page }) => { page.setDefaultTimeout(15_000); page.setDefaultNavigationTimeout(15_000); });
test.afterEach(async ({ page }) => { await page.evaluate(() => document.querySelectorAll('input[type=password]').forEach(input => { input.value = ''; })).catch(() => {}); });
test.afterAll(() => console.log('ADMIN02 explicit live HTTP assertions: ' + steps + ' requests (browser API requests additional).'));

test('RBAC real UI CRUD, role assignment, ADMIN confirmation, revoke and delete', async ({ page, request }) => {
  const actor = await account(request); const token = await adminSession(request);
  const roles = await (await call(request, 'GET', '/admin/roles?size=100', token)).json();
  const learnerId = roles.items.find(role => role.name === 'LEARNER').roleId;
  await login(page); await page.getByRole('link', { name: 'Vai trò và quyền', exact: true }).click();
  const code = 'UI_' + randomUUID().replaceAll('-', '').toUpperCase();
  await page.getByRole('button', { name: 'Tạo vai trò', exact: true }).click();
  await page.locator('#rbac-code').fill(code); await page.locator('#rbac-display').fill('RBAC Live Demo');
  await page.locator('#rbac-description').fill('Created by real browser');
  await page.getByRole('button', { name: 'Lưu vai trò', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText('Đã lưu vai trò.');
  await search(page, code); await page.locator('#rbac-display').fill('RBAC Updated');
  await page.getByRole('button', { name: 'Lưu vai trò', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText('Đã lưu vai trò.');
  await search(page, code); await page.getByLabel('content:read', { exact: true }).check();
  page.once('dialog', dialog => dialog.accept()); await page.getByRole('button', { name: 'Lưu quyền', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText('Đã lưu quyền của vai trò.');
  const role = (await (await call(request, 'GET', '/admin/roles?q=' + code, token)).json()).items[0];
  await page.getByRole('button', { name: 'Gán vai trò tài khoản', exact: true }).click(); await search(page, actor.email);
  await page.getByLabel(`RBAC Updated (${code})`, { exact: true }).check();
  page.once('dialog', dialog => dialog.accept()); await page.getByRole('button', { name: 'Lưu vai trò tài khoản', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText('Đã lưu vai trò của tài khoản.');
  expect((await call(request, 'DELETE', '/admin/roles/' + role.roleId, token)).status()).toBe(409);
  await search(page, actor.email); await page.getByLabel('ADMIN (ADMIN)', { exact: true }).check();
  page.once('dialog', dialog => { expect(dialog.message()).toContain('ADMIN'); return dialog.dismiss(); });
  await page.getByRole('button', { name: 'Lưu vai trò tài khoản', exact: true }).click();
  expect((await call(request, 'GET', '/admin/roles', actor.token)).status()).toBe(403);
  page.once('dialog', dialog => dialog.accept()); await page.getByRole('button', { name: 'Lưu vai trò tài khoản', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText('Đã lưu vai trò của tài khoản.');
  expect((await call(request, 'GET', '/admin/roles', actor.token)).status()).toBe(200);
  await search(page, actor.email); await page.getByLabel('ADMIN (ADMIN)', { exact: true }).uncheck();
  await page.getByLabel(`RBAC Updated (${code})`, { exact: true }).uncheck();
  page.once('dialog', dialog => { expect(dialog.message()).toContain('ADMIN'); return dialog.accept(); });
  await page.getByRole('button', { name: 'Lưu vai trò tài khoản', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText('Đã lưu vai trò của tài khoản.');
  expect((await call(request, 'GET', '/admin/roles', actor.token)).status()).toBe(403);
  const finalRoles = await (await call(request, 'GET', '/admin/users/' + actor.id + '/roles', token)).json();
  expect(finalRoles.roles.map(role => role.roleId)).toEqual([learnerId]);
  await page.getByRole('button', { name: 'Vai trò', exact: true }).click(); await search(page, code);
  page.once('dialog', dialog => dialog.accept()); await page.getByRole('button', { name: 'Xóa vai trò', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText('Đã xóa vai trò.');
  expect((await call(request, 'GET', '/admin/roles/' + role.roleId, token)).status()).toBe(404);
});

test('system role UI and server block self escalation; direct Learner route has no controls', async ({ page, request }) => {
  await login(page); await page.goto('/admin/roles'); await search(page, 'ADMIN');
  await expect(page.locator('#rbac-code')).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Xóa vai trò', exact: true })).toBeDisabled();
  await page.getByLabel('practice:manage:self', { exact: true }).check();
  const rejected = page.waitForResponse(response => response.url().includes('/permissions') && response.request().method() === 'PUT');
  page.once('dialog', dialog => dialog.accept()); await page.getByRole('button', { name: 'Lưu quyền', exact: true }).click();
  expect((await rejected).status()).toBe(409); await expect(page.getByRole('alert')).toContainText('Không được tự cấp thêm quyền');
  await page.reload(); await search(page, 'ADMIN'); await expect(page.getByLabel('practice:manage:self', { exact: true })).not.toBeChecked();
  const actor = await account(request); await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await login(page, actor.email, actor.password); await page.goto('/admin/roles');
  await expect(page.getByRole('alert')).toHaveText('Bạn không có quyền quản trị RBAC.');
  await expect(page.getByRole('button', { name: 'Tạo vai trò', exact: true })).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Vai trò và quyền', exact: true })).toHaveCount(0);
});

test('same live token gains and loses custom permission at the next request', async ({ request }) => {
  const actor = await account(request); const token = await adminSession(request);
  const roles = (await (await call(request, 'GET', '/admin/roles?size=100', token)).json()).items;
  const teacher = roles.find(role => role.name === 'TEACHER');
  const permissions = await (await call(request, 'GET', '/admin/permissions', token)).json();
  const examPermission = permissions.find(permission => permission.code === 'exam:manage:self');
  const code = 'HTTP_' + randomUUID().replaceAll('-', '').toUpperCase();
  const created = await call(request, 'POST', '/admin/roles', token, { name: code }); expect(created.status()).toBe(201); const role = await created.json();
  try {
    expect((await call(request, 'PUT', '/admin/users/' + actor.id + '/roles', token, { roleIds: [teacher.roleId, role.roleId] })).status()).toBe(200);
    const path = '/attempts'; const config = { sourceType: 'SECTION_SNAPSHOT', section: { type: 'READING', questionCount: 11, durationMinutes: 5 } };
    expect((await call(request, 'POST', path, actor.token, config)).status()).toBe(403);
    expect((await call(request, 'PUT', '/admin/roles/' + role.roleId + '/permissions', token, { permissionIds: [examPermission.permissionId] })).status()).toBe(200);
    expect((await call(request, 'POST', path, actor.token, config)).status()).toBe(201);
    expect((await call(request, 'PUT', '/admin/roles/' + role.roleId + '/permissions', token, { permissionIds: [] })).status()).toBe(200);
    expect((await call(request, 'POST', path, actor.token, config)).status()).toBe(403);
  } finally {
    expect((await call(request, 'PUT', '/admin/users/' + actor.id + '/roles', token, { roleIds: [teacher.roleId] })).status()).toBe(200);
    expect((await call(request, 'DELETE', '/admin/roles/' + role.roleId, token)).status()).toBe(204);
    const emptied = await call(request, 'PUT', '/admin/users/' + actor.id + '/roles', token, { roleIds: [] });
    expect(emptied.status()).toBe(200); expect((await emptied.json()).canChangeStatus).toBe(false);
    const detail = await call(request, 'GET', '/admin/users/' + actor.id + '/roles', token);
    expect(detail.status()).toBe(200); expect((await detail.json()).canChangeStatus).toBe(false);
    expect((await call(request, 'PATCH', '/admin/users/' + actor.id + '/status', token, { status: 'LOCKED' })).status()).toBe(404);
    expect((await call(request, 'PUT', '/admin/users/' + actor.id + '/roles', token, { roleIds: [teacher.roleId] })).status()).toBe(200);
  }
});
