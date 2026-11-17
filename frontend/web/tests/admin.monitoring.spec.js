import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';

// Opt-in local demo. All successful responses are real; faults only abort transport.
const demo = JSON.parse(readFileSync(new URL('../../../backend/target/local-demo-credentials.json', import.meta.url), 'utf8').replace(/^\uFEFF/, ''));
let requests = 0;
async function api(request, method, path, token, data) {
  requests++;
  return request.fetch('/api/v1' + path, { method, ...(token ? { headers: { Authorization: 'Bearer ' + token } } : {}), ...(data === undefined ? {} : { data }) });
}
async function session(request, email = demo.adminEmail, password = demo.password) {
  const response = await api(request, 'POST', '/auth/login', null, { email, password }); expect(response.status()).toBe(200); return response.json();
}
async function account(request) {
  const value = { email: 'admin07-' + randomUUID() + '@example.test', password: randomUUID() + 'Aa9!', fullName: 'ADMIN07 Browser QA' };
  expect((await api(request, 'POST', '/auth/register', null, value)).status()).toBe(201);
  return { ...value, ...await session(request, value.email, value.password) };
}
async function login(page, email = demo.adminEmail, password = demo.password) {
  await page.goto('/login'); await page.locator('#login-email').fill(email); await page.locator('#login-password').waitFor();
  await page.evaluate(value => {
    const input = document.querySelector('#login-password');
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value); input.dispatchEvent(new Event('input', { bubbles: true }));
  }, password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(); await page.waitForURL(url => url.pathname !== '/login');
}
async function createRole(request, token) {
  const response = await api(request, 'POST', '/admin/roles', token, { name: 'OBS_' + randomUUID().replaceAll('-', '') });
  expect(response.status()).toBe(201); return response.json();
}
async function filter(page, actor, action, type, resource) {
  await page.locator('#audit-actor').fill(actor); await page.locator('#audit-action').selectOption(action);
  await page.locator('#audit-type').selectOption(type); await page.locator('#audit-resource').fill(String(resource));
  await page.getByRole('button', { name: 'Lọc audit', exact: true }).click();
}
test.beforeEach(({ page }) => { page.setDefaultTimeout(15_000); page.setDefaultNavigationTimeout(15_000); });
test.afterEach(async ({ page }) => { await page.evaluate(() => document.querySelectorAll('input[type=password]').forEach(input => { input.value = ''; })).catch(() => {}); });
test.afterAll(() => console.log('ADMIN07 explicit HTTP requests: ' + requests + ' (browser API requests additional).'));

test('real admin mutations → sanitized audit → health and real Practice/Section aggregates', async ({ page, request }) => {
  const actor = await account(request); const admin = await session(request); const token = admin.accessToken;
  const beforeResponse = await api(request, 'GET', '/admin/usage-statistics', token); expect(beforeResponse.status()).toBe(200); const before = await beforeResponse.json();
  for (const config of [
    { sourceType: 'PRACTICE_SNAPSHOT', practice: { partNumbers: [5], questionCount: 1 } },
    { sourceType: 'SECTION_SNAPSHOT', section: { type: 'READING', questionCount: 11, durationMinutes: 5 } },
  ]) {
    const response = await api(request, 'POST', '/attempts', actor.accessToken, config); expect(response.status()).toBe(201); const attempt = await response.json();
    const question = attempt.questions[0];
    expect((await api(request, 'PUT', `/attempts/${attempt.attemptId}/answers/${question.questionVersionId}`, actor.accessToken, { selectedChoiceId: question.choices[0].choiceId })).status()).toBe(200);
    expect((await api(request, 'POST', `/attempts/${attempt.attemptId}/submit`, actor.accessToken)).status()).toBe(200);
    expect((await api(request, 'GET', `/attempts/${attempt.attemptId}/result`, actor.accessToken)).status()).toBe(200);
  }
  await login(page); await page.goto('/admin/roles');
  await page.getByRole('button', { name: 'Tạo vai trò', exact: true }).click(); const code = 'UIOBS_' + randomUUID().replaceAll('-', '').toUpperCase();
  await page.locator('#rbac-code').fill(code); await page.locator('#rbac-description').fill('not_returned_admin07_marker');
  await page.getByRole('button', { name: 'Lưu vai trò', exact: true }).click(); await expect(page.getByRole('status')).toHaveText('Đã lưu vai trò.');
  const roleResponse = await api(request, 'GET', '/admin/roles?q=' + code, token); expect(roleResponse.status()).toBe(200); const role = (await roleResponse.json()).items[0];
  try {
    await page.getByRole('link', { name: 'Audit quản trị', exact: true }).click();
    await filter(page, admin.user.userId, 'ROLE_CREATED', 'ROLE', role.roleId);
    await expect(page.getByText('Tìm thấy 1 bản ghi', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Xem chi tiết', exact: true }).click();
    await expect(page.getByRole('region', { name: 'Chi tiết audit' })).toBeVisible();
    await expect(page.getByRole('region', { name: 'Chi tiết audit' })).not.toContainText('not_returned_admin07_marker');
    await page.goto('/admin/users'); await page.locator('#admin-search').fill(actor.email);
    await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
    page.once('dialog', dialog => dialog.accept()); await page.getByRole('button', { name: 'Khóa', exact: true }).click();
    await expect(page.getByRole('status')).toHaveText('Đã khóa tài khoản.');
    await page.goto('/admin/audit'); await filter(page, admin.user.userId, 'USER_STATUS_CHANGED', 'USER', actor.user.userId);
    await expect(page.getByText('Tìm thấy 1 bản ghi', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Xem chi tiết', exact: true }).click(); await expect(page.getByRole('region', { name: 'Chi tiết audit' })).toContainText('LOCKED');
    expect((await api(request, 'PATCH', '/admin/users/' + actor.user.userId + '/status', token, { status: 'ACTIVE' })).status()).toBe(200);
    const after = await (await api(request, 'GET', '/admin/usage-statistics', token)).json();
    for (const name of ['practice', 'section']) {
      expect(after[name].startedCount).toBe(before[name].startedCount + 1); expect(after[name].submittedCount).toBe(before[name].submittedCount + 1);
    }
    expect(after.administrativeActivity.recordedCount).toBe(before.administrativeActivity.recordedCount + 3);
    await page.getByRole('link', { name: 'Tổng quan hệ thống', exact: true }).click();
    await expect(page.getByRole('region', { name: 'Health thành phần' }).getByRole('listitem').filter({ hasText: /^ai:/ })).toContainText('NOT_CONFIGURED');
    const attempts = page.getByRole('region', { name: 'Thống kê lượt làm bài' });
    for (const [label, key] of [['Practice', 'practice'], ['Section', 'section']]) {
      const cells = attempts.getByRole('row').filter({ hasText: label }).getByRole('cell');
      await expect(cells.nth(1)).toHaveText(String(after[key].startedCount)); await expect(cells.nth(2)).toHaveText(String(after[key].submittedCount));
    }
    await expect(page.getByRole('region', { name: 'Thống kê tài khoản' }).locator('strong')).toHaveText(String(after.users.total));
    await expect(page.getByRole('region', { name: 'Thống kê tài khoản' })).toContainText('không cộng các nhóm role');
  } finally {
    await api(request, 'PATCH', '/admin/users/' + actor.user.userId + '/status', token, { status: 'ACTIVE' });
    expect((await api(request, 'DELETE', '/admin/roles/' + role.roleId, token)).status()).toBe(204);
  }
});

test('audit paging, empty/validation and transport errors show no mock or stale report', async ({ page, request }) => {
  const admin = await session(request); const token = admin.accessToken; const role = await createRole(request, token);
  try {
    for (let i = 0; i < 21; i++)expect((await api(request, 'PATCH', '/admin/roles/' + role.roleId, token, { description: 'UI paging revision ' + i })).status()).toBe(200);
    await login(page); await page.goto('/admin/audit'); await filter(page, admin.user.userId, 'ROLE_UPDATED', 'ROLE', role.roleId);
    await expect(page.getByText('Tìm thấy 21 bản ghi', { exact: true })).toBeVisible(); await expect(page.getByRole('button', { name: 'Xem chi tiết', exact: true })).toHaveCount(20);
    await page.getByRole('button', { name: 'Trang sau', exact: true }).click(); await expect(page.getByText('Trang 2', { exact: true })).toBeVisible(); await expect(page.getByRole('button', { name: 'Xem chi tiết', exact: true })).toHaveCount(1);
    await page.route('**/api/v1/admin/audit-logs?**', route => route.abort('failed'));
    await page.getByRole('button', { name: 'Làm mới', exact: true }).click(); await expect(page.getByRole('alert')).toContainText('Không kết nối được máy chủ');
    await expect(page.getByRole('table')).toHaveCount(0); await page.unroute('**/api/v1/admin/audit-logs?**');
    await page.getByRole('button', { name: 'Làm mới', exact: true }).click(); await expect(page.getByText('Tìm thấy 21 bản ghi', { exact: true })).toBeVisible();
    await page.locator('#audit-from').fill('invalid'); await page.getByRole('button', { name: 'Lọc audit', exact: true }).click(); await expect(page.getByRole('alert')).toContainText('không hợp lệ');
    await page.locator('#audit-from').fill(''); await page.locator('#audit-actor').fill(randomUUID()); await page.getByRole('button', { name: 'Lọc audit', exact: true }).click(); await expect(page.getByText('Chưa có bản ghi phù hợp.', { exact: true })).toBeVisible();
    await page.goto('/admin/overview'); await expect(page.getByRole('region', { name: 'Thống kê tài khoản' })).toBeVisible();
    await page.route('**/api/v1/admin/usage-statistics**', route => route.abort('failed')); await page.getByRole('button', { name: 'Làm mới tổng quan', exact: true }).click();
    await expect(page.getByRole('alert')).toContainText('Không kết nối được máy chủ'); await expect(page.getByRole('region', { name: 'Thống kê tài khoản' })).toHaveCount(0);
    await expect(page.getByRole('region', { name: 'Health thành phần' })).toContainText('NOT_CONFIGURED');
  } finally {expect((await api(request, 'DELETE', '/admin/roles/' + role.roleId, token)).status()).toBe(204);}
});

test('revoking ADMIN applies to current browser/token, refreshes capability and guards direct routes', async ({ page, request }) => {
  const admin = await session(request); const actor = await account(request);
  const roles = (await (await api(request, 'GET', '/admin/roles?size=100', admin.accessToken)).json()).items;
  const learner = roles.find(role => role.name === 'LEARNER').roleId; const administrator = roles.find(role => role.administrativeRole).roleId;
  expect((await api(request, 'PUT', '/admin/users/' + actor.user.userId + '/roles', admin.accessToken, { roleIds: [learner, administrator] })).status()).toBe(200);
  await login(page, actor.email, actor.password); await page.goto('/admin/audit'); await expect(page.locator('#audit-actor')).toBeVisible();
  expect((await api(request, 'PUT', '/admin/users/' + actor.user.userId + '/roles', admin.accessToken, { roleIds: [learner] })).status()).toBe(200);
  await page.getByRole('button', { name: 'Làm mới', exact: true }).click(); await expect(page.getByRole('alert')).toHaveText('Bạn không có quyền đọc audit.');
  await expect(page.getByRole('link', { name: 'Audit quản trị', exact: true })).toHaveCount(0);
  await page.goto('/admin/overview'); await expect(page.getByRole('alert')).toHaveText('Bạn không có quyền xem tổng quan hệ thống.');
  for (const path of ['/admin/audit-logs', '/admin/health', '/admin/usage-statistics']) {
    expect((await api(request, 'GET', path, actor.accessToken)).status()).toBe(403); expect((await api(request, 'GET', path)).status()).toBe(401);
  }
});
