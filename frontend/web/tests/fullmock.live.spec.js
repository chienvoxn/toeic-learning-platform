import { test, expect } from '@playwright/test';

// Opt-in harness supplies ephemeral lab tokens via environment, never saved in this source.
const live = process.env.FULLMOCK_LIVE_SESSION ? JSON.parse(process.env.FULLMOCK_LIVE_SESSION) : null;
test.skip(!live, 'Requires the separate Full Mock rehearsal database and live HTTP server.');

async function session(page, token) {
  await page.addInitScript(token => localStorage.setItem('toeic.remote.session', JSON.stringify({ accessToken: token, tokenType: 'Bearer', expiresAt: Date.now() + 3600000 })), token);
  page.on('dialog', d => d.accept());
}

test('Full Mock real HTTP: catalog, save/replace/clear, reload, submit and persisted result', async ({ page }) => {
  await session(page, live.learner);
  await page.goto('/tests');
  await expect(page.getByRole('heading', { name: 'Bộ câu hỏi mẫu trên trình duyệt', exact: true })).toBeVisible();
  await page.getByRole('region', { name: 'Full Mock trên máy chủ' }).getByRole('link', { name: /HTTP Full Mock/ }).click();
  const create = page.waitForResponse(r => r.url().endsWith('/api/v1/attempts') && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Bắt đầu Full Mock' }).click();
  const dto = await (await create).json();
  expect(dto.sourceType).toBe('TEST_VERSION');expect(dto.test.versionId).toBe(live.versionId);
  expect(JSON.stringify(dto)).not.toMatch(/"(?:isCorrect|correctChoiceId|explanation|transcript|storageUrl)"/);
  await page.waitForURL(/\/practice\/attempts\/[0-9a-f-]+$/);
  for (const index of [1, 2, 0]) {
    const save = page.waitForResponse(r => r.url().includes('/answers/') && r.request().method() === 'PUT');
    await page.locator('.question-panel').first().locator('.answer-option').nth(index).click();expect((await save).status()).toBe(200);
  }
  const clear = page.waitForResponse(r => r.url().includes('/answers/') && r.request().method() === 'PUT');
  await page.locator('.question-panel').first().getByRole('button', { name: /Bỏ chọn/ }).click();expect((await clear).status()).toBe(200);
  await page.reload();await expect(page.locator('.question-panel').first()).toBeVisible();
  const submit = page.waitForResponse(r => r.url().endsWith('/submit') && r.request().method() === 'POST');
  await page.locator('.submit-exam-button').first().click();const result = await (await submit).json();
  expect(result.status).toBe('GRADED');expect(result.parts).toHaveLength(7);expect(result.test).toEqual(dto.test);
  await page.waitForURL(/\/result$/);await page.reload();await expect(page.getByRole('heading', { name: 'Kết quả bài thi', exact: true })).toBeVisible();
  await expect(page.getByText(/HTTP-MINI-LAB\/v1/)).toBeVisible();
  await expect(page.locator('.breadcrumb-back')).toHaveAttribute('href', '/tests');
});

test('Teacher/Admin real HTTP: fork, edit, submit, approve, publish and retire using existing Teacher UI', async ({ browser }) => {
  const teacher = await browser.newPage();await session(teacher, live.teacher);await teacher.goto('/teacher/tests');
  await teacher.getByRole('button', { name: 'HTTP Full Mock · PUBLISHED' }).click();
  await teacher.getByRole('button', { name: 'Tạo DRAFT từ bản hiện hành' }).click();
  await expect(teacher.getByText(/Phiên bản 2 · DRAFT/)).toBeVisible();
  await teacher.getByLabel('Tên bộ đề').fill('HTTP Full Mock fork');await teacher.getByRole('button', { name: 'Lưu bản nháp' }).click();
  await expect(teacher.getByText('Đã lưu bản nháp.', { exact: true })).toBeVisible();
  await teacher.getByRole('button', { name: 'Gửi duyệt', exact: true }).click();await expect(teacher.getByText(/Phiên bản 2 · IN_REVIEW/)).toBeVisible();
  const admin = await browser.newPage();await session(admin, live.admin);await admin.goto('/teacher/tests');
  await admin.getByRole('button', { name: 'HTTP Full Mock fork · PUBLISHED' }).click();
  await admin.getByRole('button', { name: 'Duyệt', exact: true }).click();await expect(admin.getByText(/Phiên bản 2 · APPROVED/)).toBeVisible();
  await admin.getByRole('button', { name: 'Xuất bản', exact: true }).click();await expect(admin.getByText(/Phiên bản 2 · PUBLISHED/)).toBeVisible();
  await admin.getByRole('button', { name: 'Ngừng cung cấp', exact: true }).click();await expect(admin.getByText(/Phiên bản 2 · RETIRED/)).toBeVisible();
  await teacher.close();await admin.close();
});
