import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';

const demo = JSON.parse(readFileSync(new URL('../../../backend/target/local-demo-credentials.json', import.meta.url), 'utf8').replace(/^\uFEFF/, ''));
let requests = 0;
async function api(request, method, path, token, data) {
  requests++;
  return request.fetch('/api/v1' + path, { method, ...(token ? { headers: { Authorization: 'Bearer ' + token } } : {}), ...(data === undefined ? {} : { data }) });
}
async function session(request, email = demo.adminEmail, password = demo.password) {
  const response = await api(request, 'POST', '/auth/login', null, { email, password }); expect(response.status()).toBe(200); return response.json();
}
async function login(page, email = demo.adminEmail, password = demo.password) {
  await page.goto('/login'); await page.locator('#login-email').fill(email); await page.locator('#login-password').waitFor();
  await page.evaluate(value => {
    const input = document.querySelector('#login-password');
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value); input.dispatchEvent(new Event('input', { bubbles: true }));
  }, password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click(); await page.waitForURL(url => url.pathname !== '/login');
}
async function createContent(request, token) {
  const title = 'ADMIN03 ' + randomUUID();
  const response = await api(request, 'POST', '/teacher/contents', token, { title, body: '<img src=x onerror=alert(1)> text-only preview', categoryCode: 'GRAMMAR' });
  expect(response.status()).toBe(201); const data = await response.json(); return { id: data.content.contentId, label: title, path: '/teacher/contents/' + data.content.contentId, data };
}
test.beforeEach(({ page }) => { page.setDefaultTimeout(15_000); page.setDefaultNavigationTimeout(15_000); });
test.afterAll(() => console.log('ADMIN03 explicit HTTP requests: ' + requests + ' (browser API requests additional).'));

test('Admin reads versions and archives real Teacher content, question and group; audit is sanitized', async ({ page, request }) => {
  const admin = await session(request); const teacher = await session(request, demo.teacherEmail); const content = await createContent(request, teacher.accessToken);
  const published = await api(request, 'GET', '/teacher/questions?status=PUBLISHED&size=100', admin.accessToken); expect(published.status()).toBe(200);
  const records = (await published.json()).content;
  const part = records.find(row => !row.groupId && row.questionType === 'SINGLE_CHOICE'); expect(part).toBeTruthy();
  const questionResponse = await api(request, 'POST', '/teacher/questions', teacher.accessToken, { partId: part.partId, questionText: 'ADMIN03 browser question', explanation: 'ADMIN03 management explanation', choices: ['A','B','C','D'].map((label, i) => ({ label, content: 'Choice ' + label, isCorrect: i === 0, displayOrder: i + 1 })) });
  expect(questionResponse.status()).toBe(201); const question = await questionResponse.json();
  const groupResponse = await api(request, 'POST', '/teacher/question-groups', teacher.accessToken, { groupType: 'PASSAGE', instruction: 'ADMIN03', passageText: 'ADMIN03 browser passage' });
  expect(groupResponse.status()).toBe(201); const group = await groupResponse.json();
  await login(page); await page.goto('/admin/content');
  for (const item of [
    { ...content, kind: 'contents', type: 'CONTENT', preview: 'text-only preview' },
    { id: question.question.questionId, label: question.question.questionId, path: '/teacher/questions/' + question.question.questionId, data: question, kind: 'questions', type: 'QUESTION', preview: 'ADMIN03 management explanation' },
    { id: group.group.groupId, label: group.group.groupId, path: '/teacher/question-groups/' + group.group.groupId, data: group, kind: 'groups', type: 'QUESTION_GROUP', preview: 'ADMIN03 browser passage' },
  ]) {
    const persistedBefore = await api(request, 'GET', item.path, teacher.accessToken); expect(persistedBefore.status()).toBe(200);
    const versionsBefore = (await persistedBefore.json()).versions;
    await page.locator('#management-kind').selectOption(item.kind); await page.locator('#management-status').selectOption('DRAFT');
    const row = page.getByRole('row').filter({ hasText: item.label }); await expect(row).toBeVisible();
    await row.getByRole('button', { name: 'Xem phiên bản', exact: true }).click(); const detail = page.getByRole('region', { name: 'Phiên bản nội dung' });
    await expect(detail).toContainText(item.preview); await expect(detail.locator('img')).toHaveCount(0); await expect(detail.locator('audio')).toHaveCount(0);
    page.once('dialog', dialog => dialog.accept()); await row.getByRole('button', { name: 'Archive', exact: true }).click();
    await expect(page.getByRole('status')).toHaveText('Đã archive. Phiên bản và lịch sử được giữ nguyên.'); await expect(row).toHaveCount(0);
    const after = await api(request, 'GET', item.path, teacher.accessToken); expect(after.status()).toBe(200); expect((await after.json()).versions).toEqual(versionsBefore);
    expect((await api(request, 'DELETE', item.path, admin.accessToken)).status()).toBe(204);
    const auditResponse = await api(request, 'GET', '/admin/audit-logs?entityType=' + item.type + '&entityId=' + item.id + '&action=' + item.type + '_ARCHIVED', admin.accessToken);
    expect(auditResponse.status()).toBe(200); const audit = await auditResponse.json(); expect(audit.totalElements).toBe(1);
    const auditDetail = await (await api(request, 'GET', '/admin/audit-logs/' + audit.items[0].auditId, admin.accessToken)).json();
    expect(auditDetail.changes).toEqual({ beforeStatus: 'DRAFT', afterStatus: 'ARCHIVED' }); expect(JSON.stringify(auditDetail)).not.toContain(item.preview);
  }
  await page.goto('/admin/audit'); await page.locator('#audit-action').selectOption('CONTENT_ARCHIVED'); await page.locator('#audit-type').selectOption('CONTENT'); await page.locator('#audit-resource').fill(content.id);
  await page.getByRole('button', { name: 'Lọc audit', exact: true }).click(); await page.getByRole('button', { name: 'Xem chi tiết', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Chi tiết audit' }).locator('pre')).toContainText('"afterStatus": "ARCHIVED"');
});

test('cancel, offline write and offline list do not claim success or show mock records', async ({ page, request }) => {
  const teacher = await session(request, demo.teacherEmail); const item = await createContent(request, teacher.accessToken);
  await login(page); await page.goto('/admin/content'); const row = page.getByRole('row').filter({ hasText: item.label });
  page.once('dialog', dialog => dialog.dismiss()); await row.getByRole('button', { name: 'Archive', exact: true }).click();
  expect((await (await api(request, 'GET', item.path, teacher.accessToken)).json()).content.status).toBe('DRAFT');
  await page.route('**/api/v1' + item.path, route => route.request().method() === 'DELETE' ? route.abort('failed') : route.continue());
  page.once('dialog', dialog => dialog.accept()); await row.getByRole('button', { name: 'Archive', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Không kết nối được máy chủ'); await expect(page.getByRole('status')).toHaveCount(0);
  expect((await (await api(request, 'GET', item.path, teacher.accessToken)).json()).content.status).toBe('DRAFT');
  await page.unroute('**/api/v1' + item.path);
  await page.route('**/api/v1/teacher/contents?**', route => route.abort('failed')); await page.getByRole('button', { name: 'Làm mới danh sách', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Không kết nối được máy chủ'); await expect(page.getByRole('table')).toHaveCount(0);
  await page.unroute('**/api/v1/teacher/contents?**'); await page.getByRole('button', { name: 'Làm mới danh sách', exact: true }).click(); await expect(row).toBeVisible();
  page.once('dialog', dialog => dialog.accept()); await row.getByRole('button', { name: 'Archive', exact: true }).click(); await expect(page.getByRole('status')).toContainText('Đã archive');
});

test('Learner denied, Teacher scoped to own content; revoking ADMIN protects the current browser/token', async ({ page, request }) => {
  const admin = await session(request); const teacher = await session(request, demo.teacherEmail); const item = await createContent(request, teacher.accessToken);
  const actor = { email: 'admin03-' + randomUUID() + '@example.test', password: randomUUID() + 'Aa9!', fullName: 'ADMIN03 Browser QA' };
  expect((await api(request, 'POST', '/auth/register', null, actor)).status()).toBe(201); const signed = await session(request, actor.email, actor.password);
  expect((await api(request, 'GET', '/teacher/contents')).status()).toBe(401);
  for (const path of ['/teacher/contents', '/teacher/questions', '/teacher/question-groups']) expect((await api(request, 'GET', path, signed.accessToken)).status()).toBe(403);
  await login(page, demo.teacherEmail); await page.goto('/admin/content'); await expect(page.getByRole('alert')).toHaveText('Bạn không có quyền quản lý loại nội dung này.'); await expect(page.getByRole('table')).toHaveCount(0);
  const roles = (await (await api(request, 'GET', '/admin/roles?size=100', admin.accessToken)).json()).items;
  const learner = roles.find(role => role.name === 'LEARNER').roleId; const administrator = roles.find(role => role.administrativeRole).roleId;
  const rolePath = '/admin/users/' + signed.user.userId + '/roles';
  const teacherRole = roles.find(role => role.name === 'TEACHER').roleId;
  expect((await api(request, 'PUT', rolePath, admin.accessToken, { roleIds: [learner, teacherRole] })).status()).toBe(200);
  expect((await api(request, 'GET', item.path, signed.accessToken)).status()).toBe(404);
  expect((await api(request, 'DELETE', item.path, signed.accessToken)).status()).toBe(404);
  const owned = await createContent(request, signed.accessToken);
  expect((await api(request, 'GET', owned.path, signed.accessToken)).status()).toBe(200);
  expect((await api(request, 'PUT', rolePath, admin.accessToken, { roleIds: [learner] })).status()).toBe(200);
  expect((await api(request, 'DELETE', owned.path, signed.accessToken)).status()).toBe(403);
  expect((await api(request, 'GET', '/teacher/tests', admin.accessToken)).status()).toBe(404);
  expect((await api(request, 'PUT', rolePath, admin.accessToken, { roleIds: [learner, administrator] })).status()).toBe(200);
  try {
    await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click(); await login(page, actor.email, actor.password); await page.goto('/admin/content'); await expect(page.getByRole('table')).toBeVisible();
    expect((await api(request, 'PUT', rolePath, admin.accessToken, { roleIds: [learner] })).status()).toBe(200);
    await page.getByRole('button', { name: 'Làm mới danh sách', exact: true }).click(); await expect(page.getByRole('alert')).toHaveText('Bạn không có quyền quản lý loại nội dung này.'); await expect(page.getByRole('table')).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'Học liệu và câu hỏi', exact: true })).toHaveCount(0);
    expect((await api(request, 'DELETE', item.path, signed.accessToken)).status()).toBe(403);
  } finally {
    expect((await api(request, 'PUT', rolePath, admin.accessToken, { roleIds: [learner] })).status()).toBe(200);
    expect((await api(request, 'DELETE', owned.path, admin.accessToken)).status()).toBe(204);
    expect((await api(request, 'DELETE', item.path, admin.accessToken)).status()).toBe(204);
  }
});
