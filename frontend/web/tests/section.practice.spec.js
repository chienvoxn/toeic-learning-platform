import { test, expect } from '@playwright/test';

// Explicit HTTP fixtures verify browser behavior; real PostgreSQL/HTTP evidence is separate.
const id = '91000000-0000-0000-0000-000000000001';
const qid = '92000000-0000-0000-0000-000000000001';
const choice = '93000000-0000-0000-0000-000000000001';
function session(type, expired = false) {
  return { attemptId: id, sourceType: 'SECTION_SNAPSHOT', status: 'IN_PROGRESS',
    totalQuestions: type === 'LISTENING' ? 7 : 6, expired,
    expiresAt: new Date(Date.now() + (expired ? -1000 : 60000)).toISOString(),
    section: { type, questionCount: type === 'LISTENING' ? 7 : 6, durationMinutes: 1 },
    answers: [], questions: [{ questionVersionId: qid, order: 1, partNumber: type === 'LISTENING' ? 1 : 5,
      questionText: 'Pinned server question', choices: [{ choiceId: choice, label: 'A', content: 'Server choice' }], passage: null }] };
}
async function setup(page, handler) {
  await page.addInitScript(() => localStorage.setItem('toeic.remote.session', JSON.stringify({ accessToken: 'section-browser-fixture', tokenType: 'Bearer', expiresAt: Date.now() + 3600000 })));
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname;
    if (path === '/api/v1/users/me') return route.fulfill({ json: { userId: 'section-fixture-user', fullName: 'Section learner', status: 'ACTIVE', roles: ['LEARNER'] } });
    if (await handler(route, path, request)) return;
    return route.fulfill({ json: {} });
  });
}
for (const type of ['LISTENING', 'READING']) {
  test(`${type} selection uses the Section contract and restored session type`, async ({ page }) => {
    const stored = session(type); let starts = 0;
    await setup(page, async (route, path, request) => {
      if (path === '/api/v1/attempts' && request.method() === 'POST') {
        starts++;
        expect(request.postDataJSON()).toEqual({ sourceType: 'SECTION_SNAPSHOT', section: { type, questionCount: stored.totalQuestions, durationMinutes: 1 } });
        await route.fulfill({ status: 201, json: stored }); return true;
      }
      if (path === `/api/v1/attempts/${id}`) { await route.fulfill({ json: stored }); return true; }
      return false;
    });
    await page.goto('/practice');
    await page.getByLabel('Chế độ').selectOption('SECTION');
    await page.getByLabel('Kỹ năng Section').selectOption(type);
    await page.getByLabel('Số câu').fill(String(stored.totalQuestions));
    await page.getByLabel('Thời lượng (phút)').fill('1');
    if (type === 'LISTENING') await expect(page.getByText('Giao diện chưa phát audio/media được bảo vệ.', { exact: false })).toBeVisible();
    await page.getByRole('button', { name: 'Bắt đầu làm bài thật' }).click();
    await expect(page).toHaveURL(new RegExp(`/practice/attempts/${id}$`));
    await expect(page.getByRole('heading', { name: new RegExp(`^Section ${type}`) })).toBeVisible();
    await page.reload();
    await expect(page.getByRole('heading', { name: new RegExp(`^Section ${type}`) })).toBeVisible();
    expect(starts).toBe(1);
  });
}

test('Part1/2 practice keeps the Practice wire contract', async ({ page }) => {
  let payload;
  await setup(page, async (route, path, request) => {
    if (path === '/api/v1/attempts' && request.method() === 'POST') {
      payload = request.postDataJSON();
      await route.fulfill({ status: 409, json: { code: 'PRACTICE_QUESTIONS_INSUFFICIENT', message: 'Fixture pool is insufficient' } }); return true;
    }
    return false;
  });
  await page.goto('/practice');
  await page.getByRole('checkbox', { name: 'Part 5', exact: true }).uncheck();
  await page.getByRole('checkbox', { name: 'Part 1', exact: true }).check();
  await page.getByRole('checkbox', { name: 'Part 2', exact: true }).check();
  await page.getByLabel('Số câu').fill('3');
  await page.getByRole('button', { name: 'Bắt đầu làm bài thật' }).click();
  await expect(page.getByRole('alert')).toContainText('Không đủ tổ hợp');
  expect(payload).toEqual({ sourceType: 'PRACTICE_SNAPSHOT', practice: { partNumbers: [1, 2], questionCount: 3 } });
});

test('insufficient Listening pool surfaces409 without fallback or another start', async ({ page }) => {
  let starts = 0;
  await setup(page, async (route, path, request) => {
    if (path === '/api/v1/attempts' && request.method() === 'POST') {
      starts++; expect(request.postDataJSON().section.type).toBe('LISTENING');
      await route.fulfill({ status: 409, json: { code: 'SECTION_QUESTIONS_INSUFFICIENT', message: 'Không đủ nguồn Section' } }); return true;
    }
    return false;
  });
  await page.goto('/practice');
  await page.getByLabel('Chế độ').selectOption('SECTION');
  await page.getByLabel('Kỹ năng Section').selectOption('LISTENING');
  await page.getByRole('button', { name: 'Bắt đầu làm bài thật' }).click();
  await expect(page.getByRole('alert')).toContainText('Không đủ tổ hợp');
  expect(starts).toBe(1); await expect(page.getByLabel('Kỹ năng Section')).toHaveValue('LISTENING');
});

test('expired restored Listening locks answers and only submits on request without a body', async ({ page }) => {
  let submissions = 0; const stored = session('LISTENING', true);
  await setup(page, async (route, path, request) => {
    if (path === `/api/v1/attempts/${id}`) { await route.fulfill({ json: stored }); return true; }
    if (path === `/api/v1/attempts/${id}/submit` || path === `/api/v1/attempts/${id}/result`) {
      if (path.endsWith('/submit')) { submissions++; expect(request.method()).toBe('POST'); expect(request.postData()).toBeNull(); }
      await route.fulfill({ json: { attemptId: id, status: 'GRADED', totalQuestions: 7, correctCount: 0, incorrectCount: 0,
        unansweredCount: 7, accuracy: 0, questions: [{ order: 1, questionVersionId: qid, partNumber: 1,
          selectedChoiceId: null, correctChoiceId: choice, isCorrect: false, explanation: 'After submit only' }] } }); return true;
    }
    return false;
  });
  await page.goto(`/practice/attempts/${id}`);
  await expect(page.getByRole('heading', { name: /^Section LISTENING/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /Server choice/ })).toBeDisabled();
  expect(submissions).toBe(0);
  page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: 'Nộp bài', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Kết quả bài thi', exact: true })).toBeVisible();
  expect(submissions).toBe(1);
});
