import { test, expect } from '@playwright/test';

const live = process.env.EXAM03_LIVE_SESSION ? JSON.parse(process.env.EXAM03_LIVE_SESSION) : null;
test.skip(!live, 'Requires a separate EXAM-03 lab DB, two live worker processes and ephemeral tokens.');
async function start(page) {
  await page.addInitScript(token => localStorage.setItem('toeic.remote.session', JSON.stringify({ accessToken: token, tokenType: 'Bearer', expiresAt: Date.now() + 3600000 })), live.learner);
  await page.goto('/tests');await page.getByRole('region', { name: 'Full Mock trên máy chủ' }).getByRole('link', { name: /HTTP Full Mock/ }).click();
  const created = page.waitForResponse(r => r.url().endsWith('/api/v1/attempts') && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Bắt đầu Full Mock' }).click();const dto = await (await created).json();
  await page.waitForURL(/\/practice\/attempts\/[0-9a-f-]+$/);return dto;
}
async function save(page, index) {
  const saved = page.waitForResponse(r => r.url().includes('/answers/') && r.request().method() === 'PUT');
  await page.locator('.question-panel').first().locator('.answer-option').nth(index).click();expect((await saved).status()).toBe(200);
}
test('expiry locks edits while offline, never claims an unsent choice was saved, then reads the worker result', async ({ page }) => {
  const dto = await start(page);await save(page, 0);
  await page.route('**/api/v1/attempts/*/answers/*', r => r.abort('internetdisconnected'));
  await page.locator('.question-panel').first().locator('.answer-option').nth(1).click();
  await expect(page.getByRole('button', { name: /Chưa lưu được/ })).toBeVisible();
  await page.context().setOffline(true);
  await page.waitForTimeout(Math.max(0, Date.parse(dto.expiresAt) - Date.now()) + 500);
  await expect(page.getByText(/Máy chủ đang tự chốt Full Mock/)).toBeVisible();
  await expect(page.locator('.question-panel').first().locator('.answer-option').first()).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Có lựa chọn chưa xác nhận đã lưu' })).toBeDisabled();
  await expect(page.getByRole('alert').filter({ hasText: 'Chưa đọc được trạng thái máy chủ' })).toBeVisible();
  await page.context().setOffline(false);
  await page.waitForURL(/\/result$/);await page.reload();await expect(page.getByRole('heading', { name: 'Kết quả bài thi', exact: true })).toBeVisible();
  const result = await page.evaluate(async id => {
    const token = JSON.parse(localStorage.getItem('toeic.remote.session')).accessToken;
    return (await fetch(`/api/v1/attempts/${id}/result`, { headers: { Authorization: `Bearer ${token}` } })).json();
  }, dto.attemptId);
  expect(result.questions[0].selectedChoiceId).toBe(dto.questions[0].choices[0].choiceId);
  expect(result.test).toEqual(dto.test);expect(result.status).toBe('GRADED');expect(result.parts).toHaveLength(7);
});
test('server grades after the browser closes, with no attempt requests during the expiry window', async ({ browser, request }) => {
  const page = await browser.newPage();const dto = await start(page);await save(page, 0);await page.close();
  await new Promise(resolve => setTimeout(resolve, Math.max(0, Date.parse(dto.expiresAt) - Date.now()) + 1000));
  const response = await request.get(`${live.api}/attempts/${dto.attemptId}/result`, { headers: { Authorization: `Bearer ${live.learner}` } });
  expect(response.status()).toBe(200);const result = await response.json();expect(result.status).toBe('GRADED');expect(result.test).toEqual(dto.test);
});
