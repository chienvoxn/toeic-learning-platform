import { test, expect } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
const file = process.env.TV2_INTEGRATION_CREDENTIALS;
const cfg = file ? JSON.parse(readFileSync(file, 'utf8')) : null;
test.skip(!cfg, 'Requires dedicated review DB private fixture actors.');
const evidence = new URL('../../../backend/target/tv2-develop-integration-20261124/', import.meta.url);
async function api(page, method, path, body) {
  return page.evaluate(async ({ method, path, body }) => {
    const token = JSON.parse(localStorage.getItem('toeic.remote.session') || 'null')?.accessToken;
    const response = await fetch('/api/v1' + path, { method, headers: { Authorization: 'Bearer ' + token, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
    return { status: response.status, data: await response.json() };
  }, { method, path, body });
}
async function openChat(page) {
  await page.goto('/settings');await page.getByRole('button', { name: 'Mở trợ lý TOEIC', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'Trợ lý TOEIC', exact: true })).toBeVisible();
}
test('real server guard: Practice allowed, Section denied across route/closed tab/restart, offline stays locked and expiry is verified', async ({ page, context }) => {
  page.setDefaultTimeout(15000);await page.goto('/login');await page.locator('#login-email').fill(cfg.actors.other.email);
  await page.evaluate(value => { const input = document.querySelector('#login-password');Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value);input.dispatchEvent(new Event('input', { bubbles: true })); }, cfg.password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();await page.waitForURL(url => url.pathname !== '/login');
  const practice = await api(page, 'POST', '/attempts', { sourceType: 'PRACTICE_SNAPSHOT', practice: { partNumbers: [5], questionCount: 1 } });expect(practice.status).toBe(201);
  await page.goto('/practice/attempts/' + practice.data.attemptId);await expect(page.locator('.question-panel')).toBeVisible();await openChat(page);
  await expect(page.getByLabel('Câu hỏi TOEIC', { exact: true })).toBeEnabled();await page.getByLabel('Câu hỏi TOEIC', { exact: true }).fill('Xin chào');
  const sent = page.waitForResponse(r => r.url().includes('/ai/conversations/') && r.request().method() === 'POST');await page.getByRole('button', { name: 'Gửi câu hỏi', exact: true }).click();
  const response = await sent;expect(response.status()).toBe(200);const conversation = new URL(response.url()).pathname.split('/')[5];
  const section = await api(page, 'POST', '/attempts', { sourceType: 'SECTION_SNAPSHOT', section: { type: 'READING', questionCount: 8, durationMinutes: 1 } });expect(section.status).toBe(201);
  const id = section.data.attemptId, pin = section.data.questions[0];
  expect((await api(page, 'PUT', `/attempts/${id}/answers/${pin.questionVersionId}`, { selectedChoiceId: pin.choices[0].choiceId })).status).toBe(200);
  await page.goto('/practice/attempts/' + id);await expect(page.locator('.question-panel').first()).toBeVisible();await openChat(page);
  await expect(page.getByLabel('Câu hỏi TOEIC', { exact: true })).toBeDisabled();
  const blocked = await api(page, 'POST', `/ai/conversations/${conversation}/messages`, { question: 'Xin chào', requestId: randomUUID() });expect(blocked.status).toBe(403);expect(blocked.data.code).toBe('AI_EXAM_RESTRICTED');
  // Simulate another browser/tab without the UI hint; backend must still enforce the exam.
  await page.evaluate(() => localStorage.removeItem('toeic.active.exam'));await page.reload();await page.getByRole('button', { name: 'Mở trợ lý TOEIC', exact: true }).click();
  await page.getByLabel('Câu hỏi TOEIC', { exact: true }).fill('Xin chào');
  const denied = page.waitForResponse(r => r.url().includes('/ai/conversations/') && r.request().method() === 'POST');await page.getByRole('button', { name: 'Gửi câu hỏi', exact: true }).click();expect((await denied).status()).toBe(403);
  await expect(page.getByLabel('Câu hỏi TOEIC', { exact: true })).toBeDisabled();
  await context.setOffline(true);await page.getByRole('button', { name: 'Kiểm tra lại trạng thái thi', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Chưa xác minh');await expect(page.getByLabel('Câu hỏi TOEIC', { exact: true })).toBeDisabled();await context.setOffline(false);
  await page.close();
  if (process.env.TV2_RESTART_SCRIPT) execFileSync('powershell.exe', ['-NoProfile', '-File', process.env.TV2_RESTART_SCRIPT], { stdio: 'ignore', timeout: 45000 });
  const next = await context.newPage();next.setDefaultTimeout(15000);
  await expect.poll(async () => { try { return (await next.request.get('/api/health')).status(); } catch { return 0; } }, { timeout: 40000 }).toBe(200);
  await openChat(next);await expect(next.getByLabel('Câu hỏi TOEIC', { exact: true })).toBeDisabled();
  const restored = await api(next, 'GET', '/attempts/' + id);expect(restored.data.questions).toEqual(section.data.questions);expect(restored.data.answers[0].selectedChoiceId).toBe(pin.choices[0].choiceId);
  await expect.poll(async () => (await api(next, 'GET', '/attempts/' + id)).data.expired, { timeout: 75000, intervals: [1000] }).toBe(true);
  const refresh = next.getByRole('button', { name: 'Kiểm tra lại trạng thái thi', exact: true });if (await refresh.isVisible()) await refresh.click();
  await expect(next.getByLabel('Câu hỏi TOEIC', { exact: true })).toBeEnabled({ timeout: 20000 });
  expect((await api(next, 'GET', `/ai/conversations/${conversation}/messages`)).status).toBe(200);
  const result = await api(next, 'POST', `/attempts/${id}/submit`);expect(result.status).toBe(200);expect((await api(next, 'POST', `/attempts/${id}/submit`)).data).toEqual(result.data);
  expect((await api(next, 'POST', `/attempts/${practice.data.attemptId}/submit`)).status).toBe(200);
  writeFileSync(new URL('PR72-browser-ai.json', evidence), JSON.stringify({ database: 'toeic_tv2_merge_http_20261124', sectionId: id, practiceAllowed: true, sectionServer403: true, removedHintCannotBypass: true, offlineCannotUnlock: true, closedTabAndRestart: !!process.env.TV2_RESTART_SCRIPT, expiredGuardedReadUnlocks: true, pinAndSavedAnswerUnchanged: true, submitReplayIdentical: true, aiProvider: 'local greeting, no Gemini/S3 mock claimed', transport: 'real browser/API/DB, no HTTP fixtures' }, null, 2));
});
