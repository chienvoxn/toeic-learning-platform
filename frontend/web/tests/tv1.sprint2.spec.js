import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';

// Opt-in live suite: isolated local demo only. No mocked API responses or SQL state edits.
const demo = JSON.parse(readFileSync(new URL('../../../backend/target/local-demo-credentials.json', import.meta.url), 'utf8').replace(/^\uFEFF/, ''));
test.beforeEach(({ page }) => {
  page.setDefaultTimeout(15_000); page.setDefaultNavigationTimeout(15_000);
  page.on('dialog', dialog => dialog.accept());
});
test.afterEach(async ({ page }) => {
  await page.evaluate(() => document.querySelectorAll('input[type=password]').forEach(input => { input.value = ''; })).catch(() => {});
});
async function call(request, method, path, token, data) {
  return request.fetch('/api/v1' + path, { method, ...(token ? { headers: { Authorization: 'Bearer ' + token } } : {}), ...(data !== undefined ? { data } : {}) });
}
async function account(request) {
  const user = { email: 'tv1-' + randomUUID() + '@example.test', password: randomUUID() + 'aA9!', fullName: 'TV1 QA Learner' };
  expect((await call(request, 'POST', '/auth/register', null, user)).status()).toBe(201);
  const response = await call(request, 'POST', '/auth/login', null, user);
  expect(response.status()).toBe(200); const session = await response.json();
  return { ...user, token: session.accessToken, id: session.user.userId };
}
async function secret(page, selector, value) {
  await page.locator(selector).waitFor();
  await page.evaluate(({ selector, value }) => {
    const input = document.querySelector(selector);
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }, { selector, value });
}
async function login(page, email, password) {
  await page.goto('/login'); await page.locator('#login-email').fill(email); await secret(page, '#login-password', password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(); await page.waitForURL(url => url.pathname !== '/login');
}

test('AUTH-04: hồ sơ/mục tiêu tự sửa, reload, xóa và lưu không đổi không gửi PATCH rỗng', async ({ page, request }) => {
  const actor = await account(request); await login(page, actor.email, actor.password);
  await page.goto('/profile'); await page.getByRole('button', { name: 'CHỈNH SỬA', exact: true }).click();
  await page.locator('#profile-name').fill('TV1 Updated Learner'); await page.getByText('Mục tiêu học tập', { exact: true }).click();
  await page.locator('#target-score').fill('750');
  const patch = page.waitForResponse(response => response.url().endsWith('/users/me') && response.request().method() === 'PATCH');
  await page.getByRole('button', { name: 'LƯU', exact: true }).click(); expect((await patch).status()).toBe(200);
  await page.reload(); await expect(page.getByRole('heading', { name: 'TV1 Updated Learner' })).toBeVisible();
  await expect(page.getByText('750', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'CHỈNH SỬA', exact: true }).click();
  let emptyPatches = 0; page.on('request', r => { if (r.method() === 'PATCH' && r.url().endsWith('/users/me')) emptyPatches++; });
  await page.getByRole('button', { name: 'LƯU', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText('Không có thay đổi để lưu.'); expect(emptyPatches).toBe(0);
  await page.getByRole('button', { name: 'CHỈNH SỬA', exact: true }).click(); await page.getByText('Mục tiêu học tập', { exact: true }).click();
  await page.locator('#target-score').fill(''); await page.getByRole('button', { name: 'LƯU', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText('Đã lưu hồ sơ.');
  expect((await (await call(request, 'GET', '/users/me', actor.token)).json()).targetScore).toBe(null);
  for (const data of [{ targetScore: 751 }, { targetScore: '750' }, { targetScore: 5 }, { userId: actor.id, fullName: 'Injected' }]) {
    expect((await call(request, 'PATCH', '/users/me', actor.token, data)).status()).toBe(400);
  }
});

test('AUTH-04: sai mật khẩu cũ không revoke; đổi đúng thu hồi hai phiên và buộc login lại', async ({ page, request }) => {
  const actor = await account(request); const other = await account(request);
  await login(page, actor.email, actor.password);
  const browserToken = await page.evaluate(() => JSON.parse(localStorage.getItem('toeic.remote.session')).accessToken);
  await page.goto('/settings');
  const next = randomUUID() + 'bB8!';
  await secret(page, '#current-password', 'IncorrectOldPassword1!'); await secret(page, '#new-password', next); await secret(page, '#confirm-password', next);
  const rejected = page.waitForResponse(r => r.url().endsWith('/users/me/password'));
  await page.getByRole('button', { name: 'Đổi mật khẩu', exact: true }).click(); expect((await rejected).status()).toBe(422);
  await expect(page.getByRole('alert')).toBeVisible(); expect((await call(request, 'GET', '/users/me', actor.token)).status()).toBe(200);
  await secret(page, '#current-password', actor.password); await secret(page, '#new-password', next); await secret(page, '#confirm-password', next);
  const changed = page.waitForResponse(r => r.url().endsWith('/users/me/password'));
  await page.getByRole('button', { name: 'Đổi mật khẩu', exact: true }).click(); expect((await changed).status()).toBe(204);
  await page.waitForURL('**/login');
  for (const token of [actor.token, browserToken]) expect((await call(request, 'GET', '/users/me', token)).status()).toBe(401);
  expect((await call(request, 'GET', '/users/me', other.token)).status()).toBe(200);
  expect((await call(request, 'POST', '/auth/login', null, { email: actor.email, password: actor.password })).status()).toBe(401);
  await login(page, actor.email, next);
});

test('ADMIN-01: tìm/lọc thật, khóa/mở khóa, phiên hiện hữu và Learner không vượt quyền', async ({ page, request }) => {
  const actor = await account(request);
  await login(page, demo.adminEmail, demo.password);
  await page.getByRole('link', { name: 'Quản lý người dùng', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Quản lý người dùng', exact: true })).toBeVisible();
  await page.locator('#admin-search').fill(actor.email); await page.locator('#admin-role').selectOption('LEARNER');
  await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  const row = page.getByRole('row').filter({ hasText: actor.email });
  await expect(row).toHaveCount(1); await expect(row.getByRole('cell', { name: 'ACTIVE', exact: true })).toBeVisible();
  const locked = page.waitForResponse(r => r.url().endsWith('/' + actor.id + '/status') && r.request().method() === 'PATCH');
  await row.getByRole('button', { name: 'Khóa', exact: true }).click(); expect((await locked).status()).toBe(200);
  await expect(row.getByRole('cell', { name: 'LOCKED', exact: true })).toBeVisible();
  expect((await call(request, 'GET', '/users/me', actor.token)).status()).toBe(401);
  expect((await call(request, 'POST', '/auth/login', null, { email: actor.email, password: actor.password })).status()).toBe(401);
  await row.getByRole('button', { name: 'Mở khóa', exact: true }).click(); await expect(row.getByRole('cell', { name: 'ACTIVE', exact: true })).toBeVisible();
  expect((await call(request, 'GET', '/users/me', actor.token)).status()).toBe(200);
  await page.locator('#admin-status').selectOption('LOCKED'); await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await expect(page.getByText('Không có tài khoản phù hợp.', { exact: true })).toBeVisible();
  await page.locator('.sidebar-link.logout-link').click(); await page.waitForURL('**/login');
  await login(page, actor.email, actor.password);
  await expect(page.getByRole('link', { name: 'Quản lý người dùng', exact: true })).toHaveCount(0);
  expect((await call(request, 'GET', '/admin/users', actor.token)).status()).toBe(403);
  expect((await call(request, 'PATCH', '/admin/users/' + actor.id + '/status', actor.token, { status: 'LOCKED' })).status()).toBe(403);
  await page.goto('/admin/users'); await expect(page.getByRole('alert')).toHaveText('Bạn không có quyền quản lý người dùng.');
  await expect(page.getByRole('table')).toHaveCount(0);
});

test('ADMIN-01: Teacher filter và network error không fallback danh sách local', async ({ page, request, context }) => {
  await login(page, demo.adminEmail, demo.password); await page.goto('/admin/users');
  await page.locator('#admin-role').selectOption('TEACHER'); await page.locator('#admin-search').fill(demo.teacherEmail);
  await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await expect(page.getByRole('row').filter({ hasText: demo.teacherEmail })).toHaveCount(1);
  const browserToken = await page.evaluate(() => JSON.parse(localStorage.getItem('toeic.remote.session')).accessToken);
  const response = await call(request, 'GET', '/admin/users?q=' + encodeURIComponent(demo.teacherEmail) + '&role=LEARNER', browserToken);
  expect(response.status()).toBe(200); expect((await response.json()).totalElements).toBe(0);
  await context.setOffline(true); await page.locator('#admin-search').fill('offline');
  await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Không kết nối được'); await expect(page.getByRole('table')).toHaveCount(0);
  await context.setOffline(false);
});
