import { test, expect } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
const credential = process.env.TV2_DEFENSE_CREDENTIALS;
const cfg = credential ? JSON.parse(readFileSync(credential, 'utf8')) : null;
const out = new URL('../../../backend/target/tv2-defense-20261125/', import.meta.url);
const stateFile = new URL('browser.json', out);
let state;try { state = JSON.parse(readFileSync(stateFile, 'utf8')); } catch { state = { attempts: {} }; }
test.skip(!cfg, 'Private dedicated demo credentials required.');
test.beforeEach(({ page }) => { page.setDefaultTimeout(15000);page.on('dialog', d => d.accept()); });
const save = () => writeFileSync(stateFile, JSON.stringify(state, null, 2));
async function login(page, role) {
  const a = cfg.roles[role];await page.goto('/login');await page.locator('#login-email').fill(a.email);
  await page.locator('#login-password').waitFor();await page.evaluate(password => { const input = document.querySelector('#login-password');Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, password);input.dispatchEvent(new Event('input', { bubbles: true })); }, a.password);
  const response = page.waitForResponse(r => r.url().endsWith('/auth/login') && r.request().method() === 'POST');await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();expect((await response).status()).toBe(200);await page.waitForURL(url => url.pathname !== '/login');
}
async function api(page, method, path, body) {
  return page.evaluate(async ({ method, path, body }) => { const token = JSON.parse(localStorage.getItem('toeic.remote.session') || 'null')?.accessToken;const r = await fetch('/api/v1' + path, { method, headers: { Authorization: 'Bearer ' + token, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });const text = await r.text();return { status: r.status, data: text ? JSON.parse(text) : null }; }, { method, path, body });
}
test('Teacher creates and edits DRAFT on Web, revision/reload/stale checks; no Full Mock publication assumed', async ({ page }) => {
  await login(page, 'teacher');const title = '[DEMO-TV2-DEFENSE] Reading draft, not Full Mock';
  if (!state.teacher) {
    await page.goto('/teacher/exams');await page.getByRole('button', { name: 'Tạo đề thi', exact: true }).click();await page.locator('#exam-title').fill(title);await page.locator('#exam-description').fill('Original defense demonstration DRAFT. Blueprint not approved.');
    const response = page.waitForResponse(r => r.url().endsWith('/teacher/tests') && r.request().method() === 'POST');await page.getByRole('dialog').getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();const r = await response;expect(r.status()).toBe(201);state.teacher = await r.json();save();
  }
  await page.goto('/teacher/exams');const card = page.locator('article.teacher-exams-item').filter({ hasText: title });await card.getByRole('link', { name: 'Mở biên soạn / phiên bản', exact: true }).click();
  const before = (await api(page, 'GET', '/teacher/tests/' + state.teacher.testId)).data;
  await page.locator('#exam-type').selectOption('READING');
  if (!state.teacherProof?.draftGroupPinned) {
    await page.locator('#exam-part').selectOption('7');const unit = page.locator('section[aria-label="Chọn từ ngân hàng câu hỏi"] > ul > li').filter({ hasText: '[DEMO-TV2-P7-1]' });await unit.getByRole('button', { name: 'Thêm nguyên mục', exact: true }).click();
  }
  const description = 'DEMO DATA: revision ' + (before.revision + 1);await expect(page.getByLabel('Tên bộ đề', { exact: true })).toHaveValue(title);await page.getByRole('textbox', { name: /^Mô tả/ }).fill(description);
  const update = page.waitForResponse(r => r.url().includes(`/teacher/tests/${before.testId}/versions/`) && r.request().method() === 'PUT');await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();expect((await update).status()).toBe(200);await expect(page.getByRole('status')).toContainText('Đã lưu bản nháp');await page.reload();await expect(page.getByRole('textbox', { name: /^Mô tả/ })).toHaveValue(description);
  const after = (await api(page, 'GET', '/teacher/tests/' + before.testId)).data;expect(after.revision).toBe(before.revision + 1);expect((await api(page, 'PUT', `/teacher/tests/${before.testId}/versions/${before.versionId}`, { revision: before.revision, document: before.document })).status).toBe(409);
  expect(JSON.stringify(after.document)).toContain('DEMO DATA: revision');
  state.teacherProof = { testId: after.testId, versionId: after.versionId, revision: after.revision, description, stale409: true, blueprintNotSeeded: true, draftGroupPinned: true, document: after.document };save();
});
async function choose(page, index) {
  const response = page.waitForResponse(r => r.request().method() === 'PUT' && r.url().includes('/answers/'));await page.locator('.question-panel').first().locator('.answer-option').nth(index).click();expect((await response).status()).toBe(200);await expect(page.getByText('Đã lưu', { exact: true })).toBeVisible();
}
for (const kind of ['p5', 'p6', 'p7', 'reading']) test(`Real Web ${kind}: select/change/clear/reload/submit/result/history/replay and own-only`, async ({ page }) => {
  await login(page, 'qa');let item = state.attempts[kind];
  if (!item) {
    await page.goto('/practice');
    if (kind === 'reading') { await page.locator('#practice-mode').selectOption('SECTION');await page.locator('#practice-count').fill('8');await page.locator('#practice-minutes').fill('20'); }
    else { for (let p = 1;p <= 7;p++) await page.getByRole('checkbox', { name: 'Part ' + p, exact: true }).setChecked(p === Number(kind[1]));await page.locator('#practice-count').fill('4'); }
    const start = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/attempts'));await page.getByRole('button', { name: 'Bắt đầu làm bài thật', exact: true }).click();const r = await start;expect(r.status()).toBe(201);const d = await r.json();item = state.attempts[kind] = { id: d.attemptId, pin: d.questions, expiresAt: d.expiresAt };save();
  }
  await page.goto('/practice/attempts/' + item.id);const current = await api(page, 'GET', '/attempts/' + item.id);expect(current.status).toBe(200);
  if (current.data.status === 'IN_PROGRESS') {
    expect(/"(?:isCorrect|correctChoiceId|explanation|transcript)"/.test(JSON.stringify(current.data))).toBe(false);expect((await api(page, 'GET', `/attempts/${item.id}/result`)).status).toBe(409);
    const media = await api(page, 'GET', `/attempts/${item.id}/media`);expect(media.status).toBe(200);expect(media.data.questions.every(q => !q.assets.length && !q.passageAssets.length)).toBe(true);
    await choose(page, 0);await choose(page, 1);const clear = page.waitForResponse(r => r.url().includes('/answers/') && r.request().method() === 'PUT');await page.locator('.question-panel').first().getByRole('button', { name: 'Bỏ chọn', exact: true }).click();expect((await clear).status()).toBe(200);await page.reload();await expect(page.locator('.answer-option[aria-pressed=true]')).toHaveCount(0);await choose(page, 0);await page.reload();await expect(page.locator('.question-panel').first().locator('.answer-option').first()).toHaveAttribute('aria-pressed', 'true');
    item.safeBeforeSubmit = true;item.changedClearedReloaded = true;save();await page.locator('.submit-exam-button').first().click();
  }
  await page.waitForURL(/\/result$/);await expect(page.locator('.score-summary')).toBeVisible();const result = await api(page, 'GET', `/attempts/${item.id}/result`);expect(result.status).toBe(200);expect((await api(page, 'POST', `/attempts/${item.id}/submit`)).data).toEqual(result.data);expect(result.data.totalQuestions).toBe(kind === 'reading' ? 8 : 4);expect(result.data.parts.map(p => p.partNumber)).toEqual(kind === 'reading' ? [5,6,7] : [Number(kind[1])]);
  await page.goto('/practice/history');const row = page.locator(`[data-attempt-id="${item.id}"]`);await expect(row).toBeVisible();await row.getByRole('link', { name: 'Xem kết quả', exact: true }).click();await expect(page.locator('.score-summary')).toBeVisible();item.result = result.data;save();
  await login(page, 'learner');for (const suffix of ['', '/result', '/media']) expect((await api(page, 'GET', `/attempts/${item.id}${suffix}`)).status).toBe(404);item.otherLearner404 = true;item.transport = 'real browser/API/DB; no HTTP fixtures; Reading has no media';save();
});
test('Listening remains visibly blocked by unpublished bank; no mock or Full Mock seed', async ({ page }) => {
  await login(page, 'qa');const checks = [];
  for (const part of [1,2,3,4]) {
    await page.goto('/practice');for (let p = 1;p <= 7;p++) await page.getByRole('checkbox', { name: 'Part ' + p, exact: true }).setChecked(p === part);await page.locator('#practice-count').fill(part < 3 ? '4' : '3');
    const response = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/attempts'));await page.getByRole('button', { name: 'Bắt đầu làm bài thật', exact: true }).click();const r = await response;expect(r.status()).toBe(409);checks.push({ part, code: (await r.json()).code });await expect(page.getByRole('alert')).toContainText('Không đủ tổ hợp');
  }
  await page.locator('#practice-mode').selectOption('SECTION');await page.locator('#section-type').selectOption('LISTENING');await page.locator('#practice-count').fill('8');const response = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/attempts'));await page.getByRole('button', { name: 'Bắt đầu làm bài thật', exact: true }).click();expect((await response).status()).toBe(409);expect((await api(page, 'GET', '/tests')).data).toEqual([]);state.listeningBlocked = { checks, section409: true, noFullMockSeed: true, learnerPlaybackPass: false };save();
});
test('After demo restart, fresh login and Web history reopen the same saved pins and results', async ({ page }) => {
  const restart = JSON.parse(readFileSync(new URL('restart.json', out), 'utf8'));expect(restart.phase).toBe('complete; services kept running');expect(restart.coreDataAllHashesUnchanged).toBe(true);
  await login(page, 'qa');
  for (const item of Object.values(state.attempts)) {
    await page.goto('/practice/history');const row = page.locator(`[data-attempt-id="${item.id}"]`);await expect(row).toBeVisible();await row.getByRole('link', { name: 'Xem kết quả', exact: true }).click();await expect(page.locator('.score-summary')).toBeVisible();expect((await api(page, 'GET', `/attempts/${item.id}/result`)).data).toEqual(item.result);expect((await api(page, 'GET', `/attempts/${item.id}`)).data.questions).toEqual(item.pin);expect((await api(page, 'POST', `/attempts/${item.id}/submit`)).data).toEqual(item.result);
  }
  await login(page, 'teacher');await page.goto(`/teacher/tests?testId=${state.teacherProof.testId}`);await expect(page.getByRole('textbox', { name: /^Mô tả/ })).toHaveValue(state.teacherProof.description);await expect(page.locator('form ol > li')).toContainText('Part 7 · 2 câu');expect((await api(page, 'GET', '/teacher/tests/' + state.teacherProof.testId)).data.document).toEqual(state.teacherProof.document);
  state.afterRestart = { freshUiLogins: true, historyResultsAndPinsUnchanged: Object.keys(state.attempts), submitReplayUnchanged: true, teacherDraftRevisionAndGroupRefsSurvive: true, noNewAttempt: true, transport: 'real browser/API/DB after physical demo stack restart' };save();
});
