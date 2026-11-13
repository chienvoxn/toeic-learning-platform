import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';

// Opt-in demo: every successful response comes from the running backend/database.
const demo = JSON.parse(readFileSync(new URL('../../../backend/target/local-demo-credentials.json', import.meta.url), 'utf8').replace(/^\uFEFF/, ''));
const LIMIT = 'vocabulary.recommendations.defaultLimit';
const DAYS = 'learning.progress.defaultDays';
let requests = 0;
async function api(request, method, path, token, data) {
  requests++;
  return request.fetch('/api/v1' + path, { method, ...(token ? { headers: { Authorization: 'Bearer ' + token } } : {}), ...(data === undefined ? {} : { data }) });
}
async function session(request, email = demo.adminEmail, password = demo.password) {
  const response = await api(request, 'POST', '/auth/login', null, { email, password }); expect(response.status()).toBe(200); return response.json();
}
async function account(request) {
  const value = { email: 'admin06-' + randomUUID() + '@example.test', password: randomUUID() + 'Aa9!', fullName: 'ADMIN06 Browser QA' };
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
async function settings(request, token) {
  const response = await api(request, 'GET', '/admin/system-settings', token); expect(response.status()).toBe(200); return response.json();
}
async function restore(request, token, original) {
  for (const row of await settings(request, token)) {
    const value = original.find(item => item.key === row.key).value;
    expect((await api(request, 'PATCH', '/admin/system-settings/' + row.key, token, { value, expectedRevision: row.revision })).status()).toBe(200);
  }
}
function editor(page, key) { return page.getByRole('form', { name: key === LIMIT ? 'Số gợi ý từ vựng mặc định' : 'Số ngày tiến độ học mặc định', exact: true }); }
async function save(page, key, value) {
  const form = editor(page, key); await form.getByRole('textbox').fill(String(value)); await form.getByRole('button', { name: 'Lưu cấu hình', exact: true }).click();
  await expect(form.getByRole('status')).toHaveText('Đã lưu cấu hình.');
}
test.beforeEach(({ page }) => { page.setDefaultTimeout(15_000); page.setDefaultNavigationTimeout(15_000); });
test.afterAll(() => console.log('ADMIN06 explicit HTTP requests: ' + requests + ' (browser API requests additional).'));

test('real UI save/reload, runtime defaults and explicit parameters, validation, audit and immutable attempts', async ({ page, request }) => {
  const admin = await session(request); const actor = await account(request); const original = await settings(request, admin.accessToken);
  for (let i = 0; i < 12; i++) expect((await api(request, 'POST', '/vocabularies', actor.accessToken, { word: 'settings-word-' + i, meaning: 'Settings demo word ' + i })).status()).toBe(201);
  const created = await api(request, 'POST', '/attempts', actor.accessToken, { sourceType: 'PRACTICE_SNAPSHOT', practice: { partNumbers: [5], questionCount: 1 } }); expect(created.status()).toBe(201);
  const attempt = await created.json();
  expect((await api(request, 'POST', `/attempts/${attempt.attemptId}/submit`, actor.accessToken)).status()).toBe(200);
  const resultPath = `/attempts/${attempt.attemptId}/result`; const pinnedResult = await (await api(request, 'GET', resultPath, actor.accessToken)).json();
  try {
    await login(page); await page.goto('/admin/settings');
    await expect(editor(page, LIMIT)).toContainText('Mặc định sản phẩm: 10'); await expect(editor(page, LIMIT)).toContainText('Giới hạn: 1–100');
    await expect(editor(page, DAYS)).toContainText('Mặc định sản phẩm: 7'); await expect(editor(page, DAYS)).toContainText('Giới hạn: 1–90');
    await save(page, LIMIT, 4); await save(page, DAYS, 11); await page.reload();
    await expect(editor(page, LIMIT).getByRole('textbox')).toHaveValue('4'); await expect(editor(page, DAYS).getByRole('textbox')).toHaveValue('11');
    const persisted = await settings(request, admin.accessToken); expect(persisted.find(item => item.key === LIMIT).value).toBe(4); expect(persisted.find(item => item.key === DAYS).value).toBe(11);
    for (const [path, length] of [['/vocabulary/recommendations', 4], ['/vocabulary/recommendations?limit=2', 2]]) {
      const response = await api(request, 'GET', path, actor.accessToken); expect(response.status()).toBe(200); expect(await response.json()).toHaveLength(length);
    }
    for (const [path, days] of [['/learning-profile/progress', 11], ['/learning-profile/progress?days=3', 3]]) {
      const response = await api(request, 'GET', path, actor.accessToken); expect(response.status()).toBe(200); expect((await response.json()).days).toBe(days);
    }
    expect(await (await api(request, 'GET', resultPath, actor.accessToken)).json()).toEqual(pinnedResult);
    const form = editor(page, DAYS); let mutations = 0;
    page.on('request', req => { if (req.method() === 'PATCH' && req.url().includes('/admin/system-settings/')) mutations++; });
    for (const bad of ['0', '91', '1.5', 'text']) {
      await form.getByRole('textbox').fill(bad); await form.getByRole('button', { name: 'Lưu cấu hình', exact: true }).click(); await expect(form.getByRole('alert')).toHaveText('Nhập số nguyên từ 1 đến 90.');
    }
    expect(mutations).toBe(0);
    const row = persisted.find(item => item.key === DAYS);
    for (const payload of [{ value: 91, expectedRevision: row.revision }, { value: '8', expectedRevision: row.revision }, { value: 8, expectedRevision: row.revision, secret: 'forbidden-field' }]) {
      expect((await api(request, 'PATCH', '/admin/system-settings/' + DAYS, admin.accessToken, payload)).status()).toBe(400);
    }
    await page.goto('/admin/audit'); await page.locator('#audit-action').selectOption('SYSTEM_SETTING_UPDATED'); await page.locator('#audit-type').selectOption('SYSTEM_SETTING'); await page.locator('#audit-resource').fill(DAYS);
    await page.getByRole('button', { name: 'Lọc audit', exact: true }).click(); await page.getByRole('button', { name: 'Xem chi tiết', exact: true }).first().click();
    await expect(page.getByRole('region', { name: 'Chi tiết audit' }).locator('pre')).toContainText('"afterValue": 11');
    await expect(page.getByRole('region', { name: 'Chi tiết audit' }).locator('pre')).toContainText('beforeRevision');
  } finally { await restore(request, admin.accessToken, original); }
});

test('two browser editors reject stale revision, refresh and transport error without fake success', async ({ page, context, request }) => {
  const admin = await session(request); const original = await settings(request, admin.accessToken); const second = await context.newPage();
  try {
    await login(page); await page.goto('/admin/settings'); await expect(editor(page, DAYS)).toBeVisible();
    await second.goto('/admin/settings'); await expect(editor(second, DAYS)).toBeVisible();
    await save(page, DAYS, 18);
    await editor(second, DAYS).getByRole('textbox').fill('19'); await editor(second, DAYS).getByRole('button', { name: 'Lưu cấu hình', exact: true }).click();
    await expect(editor(second, DAYS).getByRole('alert')).toContainText('Hãy tải lại trước khi lưu'); await expect(editor(second, DAYS).getByRole('status')).toHaveCount(0);
    expect((await settings(request, admin.accessToken)).find(item => item.key === DAYS).value).toBe(18);
    await second.getByRole('button', { name: 'Tải lại cấu hình', exact: true }).click(); await expect(editor(second, DAYS).getByRole('textbox')).toHaveValue('18'); await save(second, DAYS, 19);
    await page.route('**/api/v1/admin/system-settings', route => route.abort('failed')); await page.getByRole('button', { name: 'Tải lại cấu hình', exact: true }).click();
    await expect(page.getByRole('alert')).toContainText('Không kết nối được máy chủ'); await expect(page.getByRole('form')).toHaveCount(0);
    await page.unroute('**/api/v1/admin/system-settings'); await page.getByRole('button', { name: 'Tải lại cấu hình', exact: true }).click(); await expect(editor(page, DAYS).getByRole('textbox')).toHaveValue('19');
  } finally { await second.close(); await restore(request, admin.accessToken, original); }
});

test('guest, learner, teacher forbidden; revoked ADMIN refreshes capability for the live browser and token', async ({ page, request }) => {
  const admin = await session(request); const actor = await account(request); const teacher = await session(request, demo.teacherEmail);
  expect((await api(request, 'GET', '/admin/system-settings')).status()).toBe(401);
  for (const token of [actor.accessToken, teacher.accessToken]) {
    expect((await api(request, 'GET', '/admin/system-settings', token)).status()).toBe(403);
    expect((await api(request, 'PATCH', '/admin/system-settings/' + DAYS, token, { value: 8, expectedRevision: 0 })).status()).toBe(403);
  }
  const roles = (await (await api(request, 'GET', '/admin/roles?size=100', admin.accessToken)).json()).items;
  const learner = roles.find(role => role.name === 'LEARNER').roleId; const administrator = roles.find(role => role.administrativeRole).roleId;
  expect((await api(request, 'PUT', '/admin/users/' + actor.user.userId + '/roles', admin.accessToken, { roleIds: [learner, administrator] })).status()).toBe(200);
  try {
    await login(page, actor.email, actor.password); await page.goto('/admin/settings'); await expect(editor(page, DAYS)).toBeVisible();
    expect((await api(request, 'PUT', '/admin/users/' + actor.user.userId + '/roles', admin.accessToken, { roleIds: [learner] })).status()).toBe(200);
    await page.getByRole('button', { name: 'Tải lại cấu hình', exact: true }).click(); await expect(page.getByRole('alert')).toHaveText('Bạn không có quyền quản trị cấu hình.');
    await expect(page.getByRole('link', { name: 'Cấu hình hệ thống', exact: true })).toHaveCount(0);
    expect((await api(request, 'GET', '/admin/system-settings', actor.accessToken)).status()).toBe(403);
    await page.reload(); await expect(page.getByRole('alert')).toHaveText('Bạn không có quyền quản trị cấu hình.');
  } finally { expect((await api(request, 'PUT', '/admin/users/' + actor.user.userId + '/roles', admin.accessToken, { roleIds: [learner] })).status()).toBe(200); }
});
