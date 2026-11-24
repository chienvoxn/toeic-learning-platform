import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';

const demo = JSON.parse(readFileSync(new URL('../../../backend/target/local-demo-credentials.json', import.meta.url), 'utf8').replace(/^\uFEFF/, ''));
let requests = 0;
async function api(request, method, path, token, data) {
  requests++;
  return request.fetch('/api/v1' + path, { method, ...(token ? { headers: { Authorization: 'Bearer ' + token } } : {}), ...(data === undefined ? {} : { data }) });
}
async function session(request) {
  const response = await api(request, 'POST', '/auth/login', null, { email: demo.adminEmail, password: demo.password }); expect(response.status()).toBe(200); return response.json();
}
async function login(page) {
  await page.goto('/login'); await page.locator('#login-email').fill(demo.adminEmail); await page.locator('#login-password').waitFor();
  await page.evaluate(value => { const input = document.querySelector('#login-password'); Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value); input.dispatchEvent(new Event('input', { bubbles: true })); }, demo.password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(); await page.waitForURL(url => url.pathname !== '/login'); await page.goto('/admin/content');
}
async function save(page, method, path) {
  const response = page.waitForResponse(r => r.request().method() === method && r.url().endsWith('/api/v1' + path));
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); const result = await response; expect(result.status()).toBe(method === 'POST' ? 201 : 200);
  await expect(page.getByRole('status')).toHaveText('Đã lưu bản nháp. Bản đã publish được giữ nguyên.'); return result.json();
}
test.beforeEach(({ page }) => { page.setDefaultTimeout(15_000); page.setDefaultNavigationTimeout(15_000); });
test.afterAll(() => console.log('ADMIN03 authoring explicit HTTP requests: ' + requests + ' (browser API requests additional).'));

test('content create/edit uses partial payload, real audit and explicit assets validation', async ({ page, request }) => {
  const admin = await session(request); const title = 'UI authoring ' + randomUUID(); await login(page);
  await page.getByRole('button', { name: 'Tạo bản nháp', exact: true }).click();
  await page.locator('#edit-title').fill(title); await page.locator('#edit-summary').fill('Summary to clear'); await page.locator('#edit-body').fill('private-ui-marker');
  await page.getByRole('checkbox', { name: /Thay toàn bộ liên kết media/ }).check(); await page.locator('#media-0').fill('not-a-uuid');
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); await expect(page.getByRole('form', { name: 'Soạn nội dung' }).getByRole('alert')).toContainText('UUID');
  await page.getByRole('checkbox', { name: /Thay toàn bộ liên kết media/ }).uncheck(); const created = await save(page, 'POST', '/teacher/contents'); const id = created.content.contentId;
  try {
    const row = page.getByRole('row').filter({ hasText: title }); await row.getByRole('button', { name: 'Sửa bản nháp', exact: true }).click();
    await page.locator('#edit-title').fill(title + ' changed'); await page.locator('#edit-summary').fill('');
    const outgoing = page.waitForRequest(r => r.method() === 'PATCH' && r.url().endsWith('/teacher/contents/' + id));
    await save(page, 'PATCH', '/teacher/contents/' + id); expect((await outgoing).postDataJSON()).toEqual({ title: title + ' changed', summary: null });
    const persisted = await (await api(request, 'GET', '/teacher/contents/' + id, admin.accessToken)).json(); expect(persisted.versions).toHaveLength(1); expect(persisted.versions[0].body).toBe('private-ui-marker'); expect(persisted.versions[0].summary).toBeNull();
    const audit = await (await api(request, 'GET', '/admin/audit-logs?action=CONTENT_UPDATED&entityType=CONTENT&entityId=' + id, admin.accessToken)).json(); expect(audit.totalElements).toBe(1);
    const detail = await (await api(request, 'GET', '/admin/audit-logs/' + audit.items[0].auditId, admin.accessToken)).json();
    expect(detail.changes.requestedFields).toEqual(['summary','title']); expect(JSON.stringify(detail)).not.toContain('private-ui-marker'); expect(detail.changes.afterVersionId).toBe(persisted.versions[0].contentVersionId);
  } finally { expect((await api(request, 'DELETE', '/teacher/contents/' + id, admin.accessToken)).status()).toBe(204); }
});

test('published content edit keeps exact pointer/history and disables immutable metadata', async ({ page, request }) => {
  const admin = await session(request); const id = 'de400000-0000-4000-8000-000000000001'; const path = '/teacher/contents/' + id;
  const beforeResponse = await api(request, 'GET', path, admin.accessToken); expect(beforeResponse.status()).toBe(200); const before = await beforeResponse.json();
  const published = before.versions.find(v => v.contentVersionId === before.content.publishedVersionId); expect(published).toBeTruthy();
  const publicBefore = await (await api(request, 'GET', '/contents/' + id)).json(); const originalDraft = before.versions.find(v => v.status === 'DRAFT'); const restore = originalDraft || published;
  await login(page); await page.locator('#management-status').selectOption('PUBLISHED'); const row = page.getByRole('row').filter({ hasText: originalDraft?.title || published.title }); await row.getByRole('button', { name: 'Sửa bản nháp', exact: true }).click();
  await expect(page.locator('#edit-categoryCode')).toBeDisabled(); await expect(page.locator('#edit-difficulty')).toBeDisabled();
  try {
    await page.locator('#edit-title').fill('Published draft QA ' + randomUUID()); await save(page, 'PATCH', path);
    const after = await (await api(request, 'GET', path, admin.accessToken)).json(); expect(after.content.publishedVersionId).toBe(before.content.publishedVersionId); expect(after.versions.find(v => v.contentVersionId === published.contentVersionId)).toEqual(published);
    expect(after.versions.filter(v => v.status === 'DRAFT')).toHaveLength(1); expect(await (await api(request, 'GET', '/contents/' + id)).json()).toEqual(publicBefore);
  } finally { expect((await api(request, 'PATCH', path, admin.accessToken, { title: restore.title, summary: restore.summary, body: restore.body })).status()).toBe(200); }
});

test('group and question editors use real Part IDs, explicit pins, choices and explanations', async ({ page, request }) => {
  const admin = await session(request); const options = await (await api(request, 'GET', '/teacher/question-options', admin.accessToken)).json(); const part = options.parts.find(row => row.partNumber === 6); expect(part).toBeTruthy();
  await login(page); await page.locator('#management-kind').selectOption('groups'); await page.getByRole('button', { name: 'Tạo bản nháp', exact: true }).click();
  await page.locator('#edit-groupType').fill('PASSAGE'); await page.locator('#edit-instruction').fill('Read carefully'); await page.locator('#edit-passageText').fill('Authoring group QA');
  const group = await save(page, 'POST', '/teacher/question-groups'); const groupId = group.group.groupId; let questionId;
  try {
    await page.getByRole('row').filter({ hasText: groupId }).getByRole('button', { name: 'Sửa bản nháp', exact: true }).click(); await page.locator('#edit-passageText').fill('Edited passage'); await save(page, 'PATCH', '/teacher/question-groups/' + groupId);
    await page.locator('#management-kind').selectOption('questions'); await page.getByRole('button', { name: 'Tạo bản nháp', exact: true }).click(); await page.locator('#edit-partId').selectOption(String(part.partId));
    await page.locator('#edit-questionText').fill('UI group-pinned question'); await page.locator('#edit-explanation').fill('private-answer-marker');
    for (const label of ['A','B','C','D']) await page.locator('#choice-' + label).fill('Answer ' + label);
    await page.getByRole('radio', { name: 'Đúng B', exact: true }).check(); await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
    await expect(page.getByRole('form', { name: 'Soạn nội dung' }).getByRole('alert')).toContainText('cần phiên bản nhóm');
    await page.locator('#edit-groupVersionId').fill(group.versions[0].groupVersionId); const created = await save(page, 'POST', '/teacher/questions'); questionId = created.question.questionId;
    await page.getByRole('row').filter({ hasText: questionId }).getByRole('button', { name: 'Sửa bản nháp', exact: true }).click(); await page.locator('#edit-explanation').fill(''); await page.getByRole('radio', { name: 'Đúng C', exact: true }).check(); await save(page, 'PATCH', '/teacher/questions/' + questionId);
    const persisted = await (await api(request, 'GET', '/teacher/questions/' + questionId, admin.accessToken)).json(); expect(persisted.versions[0].groupVersionId).toBe(group.versions[0].groupVersionId); expect(persisted.versions[0].explanation).toBeNull(); expect(persisted.versions[0].choices.find(row => row.isCorrect).label).toBe('C');
    await expect(page.getByRole('button', { name: /publish|duyệt|xuất bản/i })).toHaveCount(0);
  } finally { if (questionId) expect((await api(request, 'DELETE', '/teacher/questions/' + questionId, admin.accessToken)).status()).toBe(204); expect((await api(request, 'DELETE', '/teacher/question-groups/' + groupId, admin.accessToken)).status()).toBe(204); }
});

test('offline create cannot report success and retry persists exactly one draft', async ({ page, request }) => {
  const admin = await session(request); const title = 'Offline create QA ' + randomUUID(); await login(page); await page.getByRole('button', { name: 'Tạo bản nháp', exact: true }).click(); await page.locator('#edit-title').fill(title);
  await page.route('**/api/v1/teacher/contents', route => route.request().method() === 'POST' ? route.abort('failed') : route.continue()); await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByRole('form', { name: 'Soạn nội dung' }).getByRole('alert')).toContainText('Không kết nối được máy chủ'); await expect(page.getByRole('status')).toHaveCount(0);
  await page.unroute('**/api/v1/teacher/contents'); const created = await save(page, 'POST', '/teacher/contents');
  await expect(page.getByRole('alert')).toHaveCount(0);
  const rows = await (await api(request, 'GET', '/teacher/contents?size=100', admin.accessToken)).json(); expect(rows.content.filter(row => row.title === title)).toHaveLength(1);
  expect((await api(request, 'DELETE', '/teacher/contents/' + created.content.contentId, admin.accessToken)).status()).toBe(204);
});
