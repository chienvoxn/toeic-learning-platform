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
  let currentAttempt = structuredClone(attempt);
  const result = {
    attemptId,
    status: 'GRADED',
    submittedAt: '2026-11-24T00:05:00Z',
    totalQuestions: 1,
    correctCount: 1,
    incorrectCount: 0,
    unansweredCount: 0,
    rawScore: 1,
    accuracy: 100,
    parts: [{ partNumber: 5, correctCount: 1, totalQuestions: 1 }],
    questions: [{
      questionVersionId: attempt.questions[0].questionVersionId,
      selectedChoiceId: attempt.questions[0].choices[0].choiceId,
      correctChoiceId: attempt.questions[0].choices[0].choiceId,
      isCorrect: true,
      explanation: 'Answer A is correct.',
    }],
  };
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
      return json(currentAttempt);
    }
    if (path.endsWith(`/attempts/${attemptId}/answers/${attempt.questions[0].questionVersionId}`) && request.method() === 'PUT') {
      const body = request.postDataJSON();
      currentAttempt = {
        ...currentAttempt,
        answers: [{ questionVersionId: attempt.questions[0].questionVersionId, selectedChoiceId: body.selectedChoiceId }],
      };
      return json({});
    }
    if (path.endsWith(`/attempts/${attemptId}/submit`) && request.method() === 'POST') {
      currentAttempt = { ...currentAttempt, status: 'GRADED' };
      return json(result);
    }
    if (path.endsWith(`/attempts/${attemptId}/result`) && request.method() === 'GET') return json(result);
    if (path.endsWith(`/attempts/${attemptId}`) && request.method() === 'GET') return json(currentAttempt);
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
  await page.locator('.question-panel .answer-option').first().click();
  await expect(page.getByText('Đã lưu', { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.locator('.question-panel .answer-option').first()).toHaveAttribute('aria-pressed', 'true');
  await page.getByRole('button', { name: 'Nộp bài', exact: true }).first().click();
  await expect(page).toHaveURL(new RegExp(`/practice/attempts/${attemptId}/result$`));
  await expect(page.getByRole('heading', { name: 'Kết quả bài thi', exact: true })).toBeVisible();
  await expect(page.getByText('Điểm thô: 1')).toBeVisible();
  await page.getByRole('button', { name: 'Mở đáp án', exact: true }).click();
  await expect(page.getByText('Answer A is correct.')).toBeVisible();
  expect(startRequests).toBe(1);
});

test('Full Test hết giờ khóa đáp án và nút nộp bài', async ({ page }) => {
  const expiredAttempt = {
    ...attempt,
    expiresAt: '2026-11-24T00:00:00Z',
    expired: true,
  };
  await page.addInitScript(({ key }) => {
    localStorage.setItem(key, JSON.stringify({ accessToken: 'browser-fixture-token', tokenType: 'Bearer', expiresAt: Date.now() + 60_000 }));
  }, { key: sessionKey });
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const json = body => route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
    if (path.endsWith('/users/me')) {
      return json({ userId: 'learner-fixture', email: 'learner@example.test', fullName: 'Learner Fixture', roles: ['LEARNER'], status: 'ACTIVE' });
    }
    if (path.endsWith(`/attempts/${attemptId}`)) return json(expiredAttempt);
    return route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 'UNEXPECTED_REQUEST', message: path }) });
  });

  await page.goto(`/practice/attempts/${attemptId}`);
  await expect(page.getByRole('status')).toContainText('Full Test đã hết hạn và không nhận nộp mới.');
  await expect(page.locator('.question-panel .answer-option').first()).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Nộp bài', exact: true }).first()).toBeDisabled();
});

test('Full Test tải lại học liệu sau lỗi mạng mà vẫn giữ phiên làm bài', async ({ page }) => {
  let mediaRequests = 0;
  const mediaAttempt = {
    ...attempt,
    questions: [{
      ...attempt.questions[0],
      assets: [{ resourceId: 'audio-fixture', resourceType: 'AUDIO' }],
    }],
  };
  await page.addInitScript(({ key }) => {
    localStorage.setItem(key, JSON.stringify({ accessToken: 'browser-fixture-token', tokenType: 'Bearer', expiresAt: Date.now() + 60_000 }));
  }, { key: sessionKey });
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const json = body => route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
    if (path.endsWith('/users/me')) {
      return json({ userId: 'learner-fixture', email: 'learner@example.test', fullName: 'Learner Fixture', roles: ['LEARNER'], status: 'ACTIVE' });
    }
    if (path.endsWith(`/attempts/${attemptId}/media`)) {
      mediaRequests += 1;
      // React StrictMode mounts the page twice in development. Both initial
      // requests fail, while the explicit retry below succeeds.
      if (mediaRequests <= 2) return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 'MEDIA_UNAVAILABLE' }) });
      return json({
        attemptId,
        sourceType: 'TEST_VERSION',
        status: 'IN_PROGRESS',
        questions: [{
          questionVersionId: attempt.questions[0].questionVersionId,
          assets: [{ resourceId: 'audio-fixture', resourceType: 'AUDIO', mimeType: 'audio/mpeg', url: 'https://media.example.test/audio.mp3' }],
          passageAssets: [],
        }],
      });
    }
    if (path.endsWith(`/attempts/${attemptId}`)) return json(mediaAttempt);
    return route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 'UNEXPECTED_REQUEST', message: path }) });
  });

  await page.goto(`/practice/attempts/${attemptId}`);
  await expect(page.getByRole('status')).toContainText('Không tải được audio hoặc hình minh họa.');
  await page.getByRole('button', { name: 'Tải lại học liệu', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Tải lại học liệu', exact: true })).toHaveCount(0);
  await expect(page.locator('.question-panel')).toBeVisible();
  expect(mediaRequests).toBe(3);
});
