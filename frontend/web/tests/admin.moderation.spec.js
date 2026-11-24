import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
const demo = JSON.parse(readFileSync(new URL('../../../backend/target/local-demo-credentials.json', import.meta.url), 'utf8').replace(/^\uFEFF/, ''));
let requests = 0;
async function api(request, method, path, token, data) {
  requests++; return request.fetch('/api/v1' + path, { method, ...(token ? { headers: { Authorization: 'Bearer ' + token } } : {}), ...(data === undefined ? {} : { data }) });
}
async function session(request, email) {
  const r = await api(request, 'POST', '/auth/login', null, { email, password: demo.password }); expect(r.status()).toBe(200); return r.json();
}
async function login(page, email) {
  page.on('dialog', dialog => dialog.accept()); await page.goto('/login'); await page.locator('#login-email').fill(email);
  await page.evaluate(value => { const input = document.querySelector('#login-password'); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value); input.dispatchEvent(new Event('input', { bubbles: true })); }, demo.password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(); await page.waitForURL(url => url.pathname !== '/login');
}
async function open(page, version) {
  await page.goto('/moderation'); await page.locator('#moderation-kind').selectOption('contents');
  await page.getByRole('row').filter({ hasText: version }).getByRole('button', { name: 'Xem duyệt' }).click();
  await expect(page.getByRole('region', { name: 'Chi tiết duyệt' })).toBeVisible();
}
async function command(page, label, action) {
  const response = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/' + action));
  await page.getByRole('button', { name: label, exact: true }).click(); const result = await response; expect(result.status()).toBe(200); return result.json();
}
async function edit(page, title, replacement) {
  await page.goto('/teacher/content'); await page.locator('#management-kind').selectOption('contents');
  await page.getByRole('row').filter({ hasText: title }).getByRole('button', { name: 'Sửa bản nháp', exact: true }).click();
  await page.locator('#edit-title').fill(replacement); const response = page.waitForResponse(r => r.request().method() === 'PATCH' && r.url().includes('/teacher/contents/'));
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); const result = await response; expect(result.status()).toBe(200); return result.json();
}
test.afterAll(() => console.log('ADMIN03 moderation explicit HTTP requests: ' + requests + ' (UI calls additional).'));
test('Teacher draft -> submit -> Admin return -> edit -> resubmit -> approve -> publish -> Learner read; next draft preserves publication', async ({ browser, request }) => {
  const contexts = await Promise.all(Array.from({ length: 3 }, () => browser.newContext({ baseURL: process.env.WEB_AUTH_BASE_URL || 'http://127.0.0.1:5173' })));
  const [teacherPage, adminPage, learnerPage] = await Promise.all(contexts.map(context => context.newPage()));
  const teacher = await session(request, demo.teacherEmail); let id;
  try {
    await login(teacherPage, demo.teacherEmail); await login(adminPage, demo.adminEmail); await login(learnerPage, demo.learnerEmail);
    await teacherPage.goto('/teacher/content'); await teacherPage.locator('#management-kind').selectOption('contents'); await teacherPage.getByRole('button', { name: 'Tạo bản nháp', exact: true }).click();
    const title = 'Workflow UI ' + randomUUID(); await teacherPage.locator('#edit-title').fill(title); await teacherPage.locator('#edit-body').fill('Teacher review lifecycle text');
    const createdResponse = teacherPage.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/teacher/contents'));
    await teacherPage.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); const response = await createdResponse; expect(response.status()).toBe(201); const created = await response.json(); id = created.content.contentId; const version = created.versions[0].contentVersionId;
    expect((await api(request, 'GET', '/contents/' + id)).status()).toBe(404);
    await open(teacherPage, version); await command(teacherPage, 'Gửi duyệt', 'submit');
    await open(adminPage, version); await adminPage.locator('#moderation-reason').fill('Sửa tiêu đề trước khi xuất bản'); await command(adminPage, 'Trả sửa', 'return');
    await edit(teacherPage, title, title + ' revised'); await open(teacherPage, version); await command(teacherPage, 'Gửi duyệt', 'submit');
    await open(adminPage, version); await command(adminPage, 'Duyệt', 'approve');
    const rejected = await api(request, 'PATCH', '/teacher/contents/' + id, teacher.accessToken, { title: 'Cannot edit approval' }); expect(rejected.status()).toBe(409);
    await adminPage.locator('#moderation-visibility').selectOption('PUBLIC'); await command(adminPage, 'Xuất bản', 'publish');
    await learnerPage.goto('/catalog/' + id); await expect(learnerPage.getByRole('heading', { name: title + ' revised', exact: true })).toBeVisible();
    const published = await (await api(request, 'GET', '/contents/' + id)).json(); expect(published.contentVersionId).toBe(version);
    const next = await edit(teacherPage, title + ' revised', title + ' next draft'); expect(next.content.publishedVersionId).toBe(version); expect(next.content.workingDraftVersionId).not.toBe(version);
    expect(await (await api(request, 'GET', '/contents/' + id)).json()).toEqual(published);
    await learnerPage.reload(); await expect(learnerPage.getByRole('heading', { name: title + ' revised', exact: true })).toBeVisible();
    const admin = await session(request, demo.adminEmail); const audit = await api(request, 'GET', '/admin/audit-logs?action=CONTENT_PUBLISHED&entityId=' + id, admin.accessToken); expect(audit.status()).toBe(200); expect((await audit.json()).totalElements).toBe(1);
  } finally {
    if (id) expect((await api(request, 'DELETE', '/teacher/contents/' + id, teacher.accessToken)).status()).toBe(204);
    await Promise.all(contexts.map(context => context.close()));
  }
});
test('Teacher cannot review, stale submit rejects, offline UI does not claim saved transition', async ({ page, request }) => {
  const teacher = await session(request, demo.teacherEmail); const learner = await session(request, demo.learnerEmail);
  const created = await api(request, 'POST', '/teacher/contents', teacher.accessToken, { title: 'Moderation offline ' + randomUUID(), body: 'Text only', categoryCode: 'GRAMMAR' }); expect(created.status()).toBe(201);
  const d = await created.json(); const id = d.content.contentId; const version = d.versions[0].contentVersionId; const path = '/moderation/contents/' + id + '/versions/' + version;
  try {
    expect((await api(request, 'POST', path + '/approve', teacher.accessToken, { expectedRevision: 0 })).status()).toBe(403);
    expect((await api(request, 'GET', path, learner.accessToken)).status()).toBe(403);
    expect((await api(request, 'POST', path + '/submit', teacher.accessToken, { expectedRevision: 999 })).status()).toBe(409);
    await login(page, demo.teacherEmail); await open(page, version);
    await page.route('**/moderation/contents/*/versions/*/submit', route => route.abort());
    await page.getByRole('button', { name: 'Gửi duyệt', exact: true }).click(); await expect(page.getByRole('alert')).toBeVisible(); await expect(page.getByRole('status')).toHaveCount(0);
    const unchanged = await (await api(request, 'GET', path, teacher.accessToken)).json(); expect(unchanged.status).toBe('DRAFT'); expect(unchanged.revision).toBe(0);
  } finally {expect((await api(request, 'DELETE', '/teacher/contents/' + id, teacher.accessToken)).status()).toBe(204);}
});
