import { test, expect } from '@playwright/test';

const sessionKey = 'toeic.remote.session';
const testVersionId = '11111111-1111-4111-8111-111111111111';
const attemptId = '22222222-2222-4222-8222-222222222222';

const attempt = {
  attemptId,
  sourceType: 'TEST_VERSION',
  status: 'IN_PROGRESS',
  startedAt: '2026-11-24T00:00:00Z',
  expiresAt: '2030-11-24T02:00:00Z',
  expired: false,
  totalQuestions: 1,
  answers: [],
  questions: [{
    order: 1,
    questionId: '33333333-3333-4333-8333-333333333333',
    questionVersionId: '44444444-4444-4444-8444-444444444444',
    partNumber: 5,
    questionText: 'Choose the best answer.',
    choices: [
      { choiceId: '55555555-5555-4555-8555-555555555555', label: 'A', content: 'Answer A' },
      { choiceId: '66666666-6666-4666-8666-666666666666', label: 'B', content: 'Answer B' },
    ],
  }],
};

test('Full Test: chọn đề đã xuất bản, tạo phiên đúng phiên bản và vào phòng thi', async ({ page }) => {
  let startRequests = 0;
  page.on('dialog', dialog => dialog.accept());
  await page.addInitScript(({ key }) => {
    localStorage.setItem(key, JSON.stringify({ accessToken: 'browser-fixture-token', tokenType: 'Bearer', expiresAt: Date.now() + 60_000 }));
  }, { key: sessionKey });
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const json = body => route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
    if (path.endsWith('/users/me') && request.method() === 'GET') {
      return json({ userId: 'learner-fixture', email: 'learner@example.test', fullName: 'Learner Fixture', roles: ['LEARNER'], status: 'ACTIVE' });
    }
    if (path.endsWith('/full-tests') && request.method() === 'GET') {
      return json([{ testId: 'test-fixture', testVersionId, title: 'TOEIC Full Test Fixture', description: 'Đề kiểm thử giao diện', durationSeconds: 7200, totalQuestions: 200, publishedAt: '2026-11-24T00:00:00Z' }]);
    }
    if (path.endsWith(`/full-tests/${testVersionId}/attempts`) && request.method() === 'POST') {
      startRequests += 1;
      return json(attempt);
    }
    if (path.endsWith(`/attempts/${attemptId}`) && request.method() === 'GET') return json(attempt);
    return route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 'UNEXPECTED_REQUEST', message: path }) });
  });

  await page.goto('/tests');
  await expect(page.getByRole('heading', { name: 'TOEIC Full Test Fixture', exact: true })).toBeVisible();
  await page.getByRole('link', { name: /BẮT ĐẦU/ }).first().click();
  await expect(page).toHaveURL(new RegExp(`/practice\\?mode=FULL_TEST&testVersionId=${testVersionId}$`));
  await expect(page.getByText('Đề đã chọn:')).toContainText('TOEIC Full Test Fixture');
  await page.getByRole('button', { name: 'Bắt đầu làm bài thật', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/practice/attempts/${attemptId}$`));
  await expect(page.getByRole('heading', { name: 'ENGLINGO — Full TOEIC Listening & Reading', exact: true })).toBeVisible();
  await expect(page.getByText('Choose the best answer.')).toBeVisible();
  expect(startRequests).toBe(1);
});
