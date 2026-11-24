import { test, expect } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';

// Real BE/DB only. Caller supplies a private fixture credential file, never committed.
const credentialFile = process.env.TV2_INTEGRATION_CREDENTIALS;
const cfg = credentialFile ? JSON.parse(readFileSync(credentialFile, 'utf8')) : null;
const demo = cfg ? JSON.parse(readFileSync(new URL('../../../backend/target/local-demo-credentials.json', import.meta.url), 'utf8').replace(/^\uFEFF/, '')) : null;
const evidence = new URL('../../../backend/target/tv2-develop-integration-20261124/', import.meta.url);
test.skip(!cfg, 'Dedicated restored DB and private fixture accounts required.');
test.beforeEach(async ({ page }) => { page.setDefaultTimeout(15_000);page.on('dialog', d => d.accept()); });
async function login(page, who = 'existing') {
  await page.goto('/login');
  const email = who === 'existing' ? demo.learnerEmail : cfg.actors[who].email;
  const password = who === 'existing' ? demo.password : cfg.password;
  await page.locator('#login-email').fill(email);
  await page.locator('#login-password').waitFor();
  await page.evaluate(value => {
    const input = document.querySelector('#login-password');
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }, password);
  const response = page.waitForResponse(r => r.url().endsWith('/auth/login') && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  expect((await response).status()).toBe(200);
  await page.waitForURL(url => url.pathname !== '/login');
}
async function api(page, method, path, body) {
  return page.evaluate(async ({ method, path, body }) => {
    const token = JSON.parse(localStorage.getItem('toeic.remote.session') || 'null')?.accessToken;
    const response = await fetch('/api/v1' + path, { method, headers: { Authorization: 'Bearer ' + token, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
    return { status: response.status, data: await response.json() };
  }, { method, path, body });
}
async function choose(page, index) {
  const response = page.waitForResponse(r => r.request().method() === 'PUT' && r.url().includes('/answers/'));
  await page.locator('.question-panel').first().locator('.answer-option').nth(index).click();
  expect((await response).status()).toBe(200);
  await expect(page.getByText('Đã lưu', { exact: true })).toBeVisible();
}
async function submit(page) {
  await page.locator('.submit-exam-button').first().click();
  await page.waitForURL(/\/result$/);
  await expect(page.locator('.score-summary')).toBeVisible();
}
test('New Teacher shell creates and edits the canonical draft; server revision and permissions remain enforced', async ({ page }) => {
  await login(page, 'teacher');await page.goto('/teacher/exams');
  await expect(page.getByRole('heading', { name: 'Ngân hàng đề thi', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Tạo đề thi', exact: true }).click();
  const title = 'Merged Teacher draft ' + randomUUID();
  await page.locator('#exam-title').fill(title);await page.locator('#exam-description').fill('Real UI/API draft');
  const response = page.waitForResponse(r => r.url().endsWith('/teacher/tests') && r.request().method() === 'POST');
  await page.getByRole('dialog').getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  const created = await response;expect(created.status()).toBe(201);const draft = await created.json();
  const card = page.locator('article.teacher-exams-item').filter({ hasText: title });await expect(card).toBeVisible();
  await card.getByRole('link', { name: 'Mở biên soạn / phiên bản', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Biên soạn bộ đề' })).toBeVisible();
  await expect(page.getByLabel('Tên bộ đề', { exact: true })).toHaveValue(title);
  await page.getByRole('textbox', { name: /^Mô tả/ }).fill('Edited via shared editor');
  const update = page.waitForResponse(r => r.url().includes(`/teacher/tests/${draft.testId}/versions/`) && r.request().method() === 'PUT');
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  expect((await update).request().postDataJSON().document.description).toBe('Edited via shared editor');
  await expect(page.getByRole('status')).toContainText('Đã lưu bản nháp');
  await page.reload();await expect(page.getByRole('textbox', { name: /^Mô tả/ })).toHaveValue('Edited via shared editor');
  const value = await api(page, 'GET', '/teacher/tests/' + draft.testId);expect(value.status).toBe(200);expect(value.data.revision).toBe(1);
  expect((await api(page, 'PUT', `/teacher/tests/${draft.testId}/versions/${draft.versionId}`, { revision: 0, document: value.data.document })).status).toBe(409);
  await page.goto('/teacher/questions/new');await expect(page.getByRole('button', { name: 'Lưu bản nháp', exact: true })).toBeEnabled();
  await page.getByLabel('Nội dung câu hỏi *').fill('Original integration draft question');
  for (const [i, label] of ['A', 'B', 'C', 'D'].entries()) await page.getByPlaceholder('Nội dung đáp án ' + label).fill('Choice ' + i);
  const question = page.waitForResponse(r => r.url().endsWith('/teacher/questions') && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();expect((await question).status()).toBe(201);
  writeFileSync(new URL('browser-teacher.json', evidence), JSON.stringify({ testId: draft.testId, titleOnlyToRevisionEditor: true, stale409: true, questionPartFromReferenceApi: true, transport: 'real browser/HTTP/DB, no route stubs' }, null, 2));
});
test('Restored existing learner reads old results and new Practice/Reading/history end to end', async ({ page }) => {
  await login(page);
  const old = await api(page, 'GET', '/attempts?page=0&size=100');expect(old.status).toBe(200);
  let oldResults = 0;
  for (const row of old.data.items.filter(r => r.status === 'GRADED')) {
    const result = await api(page, 'GET', `/attempts/${row.attemptId}/result`);expect(result.status).toBe(200);
    expect((await api(page, 'POST', `/attempts/${row.attemptId}/submit`)).data).toEqual(result.data);oldResults++;
  }
  expect(oldResults).toBeGreaterThan(0);
  const results = [];
  for (const section of [false, true]) {
    await page.goto('/practice');
    if (section) await page.locator('#practice-mode').selectOption('SECTION');
    const response = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/attempts'));
    await page.getByRole('button', { name: 'Bắt đầu làm bài thật', exact: true }).click();
    expect((await response).status()).toBe(201);await page.waitForURL(/\/practice\/attempts\/[a-f0-9-]+$/);
    const id = new URL(page.url()).pathname.split('/').pop();
    const before = (await api(page, 'GET', '/attempts/' + id)).data;
    expect(/"(?:isCorrect|correctChoiceId|explanation|transcript)"/.test(JSON.stringify(before))).toBe(false);
    await choose(page, 0);await choose(page, 1);
    const clear = page.waitForResponse(r => r.url().includes('/answers/') && r.request().method() === 'PUT');
    await page.locator('.question-panel').first().getByRole('button', { name: 'Bỏ chọn', exact: true }).click();expect((await clear).status()).toBe(200);
    await page.reload();await expect(page.locator('.answer-option[aria-pressed=true]')).toHaveCount(0);await choose(page, 0);
    await page.reload();await expect(page.locator('.question-panel').first().locator('.answer-option').first()).toHaveAttribute('aria-pressed', 'true');
    await submit(page);const result = (await api(page, 'GET', `/attempts/${id}/result`)).data;
    expect((await api(page, 'POST', `/attempts/${id}/submit`)).data).toEqual(result);
    if (section) { expect(result.parts.map(p => p.partNumber)).toEqual([5,6,7]);expect(result.totalQuestions).toBe(8); }
    await page.goto('/practice/history');const row = page.locator(`[data-attempt-id="${id}"]`);await expect(row).toBeVisible();
    await row.getByRole('link', { name: 'Xem kết quả', exact: true }).click();await expect(page.locator('.score-summary')).toBeVisible();
    results.push({ id, section, result });
  }
  await page.goto('/tests');await expect(page.getByRole('heading', { name: 'Thư viện đề thi TOEIC' })).toBeVisible();
  expect((await api(page, 'GET', '/tests')).data).toEqual([]);
  expect((await api(page, 'GET', '/teacher/tests')).status).toBe(403);
  writeFileSync(new URL('browser-learner.json', evidence), JSON.stringify({ oldSavedResultsReadableAndReplayIdentical: oldResults, results, transport: 'real browser/HTTP/restored local demo clone, no stubs' }, null, 2));
});
test('Moderation UI reads the integrated G1 evidence; batch API stays permission protected', async ({ page }) => {
  await login(page, 'admin');await page.goto('/moderation');
  await page.locator('#moderation-kind').selectOption('groups');
  await expect(page.getByRole('button', { name: 'Xem duyệt', exact: true }).first()).toBeVisible();
  await page.getByRole('button', { name: 'Xem duyệt', exact: true }).first().click();
  await expect(page.getByRole('region', { name: 'Chi tiết duyệt' })).toBeVisible();
  const list = await api(page, 'GET', '/moderation/groups?page=0');expect(list.status).toBe(200);
  expect(list.data.items.some(row => row.status === 'PUBLISHED')).toBe(true);
  writeFileSync(new URL('browser-moderation.json', evidence), JSON.stringify({ realUiReadPublicationEvidence: true, batchWriteEvidence: 'http-g1-proof.json uses real API and DB; batch button is not implemented in this UI', transport: 'real browser/HTTP/DB, no stubs' }, null, 2));
});
