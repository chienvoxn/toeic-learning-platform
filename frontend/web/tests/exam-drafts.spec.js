import { test, expect } from '@playwright/test';

const unit = { partNumber: 3, questions: [{ questionVersionId: 'q3', groupVersionId: 'g1', questionText: 'Nhóm câu hỏi mẫu 3' }, { questionVersionId: 'q2', groupVersionId: 'g1', questionText: 'Nhóm câu hỏi mẫu 2' }] };
async function setup(page, failure) {
  let draft; const calls = [];
  await page.addInitScript(() => localStorage.setItem('toeic.remote.session', JSON.stringify({ accessToken: 'draft-browser-fixture', tokenType: 'Bearer', expiresAt: Date.now() + 3600000 })));
  await page.route('**/api/v1/**', async route => {
    const req = route.request(), path = new URL(req.url()).pathname;
    if (path === '/api/v1/users/me') return route.fulfill({ json: { userId: 'teacher', fullName: 'Teacher', status: 'ACTIVE', roles: ['TEACHER'] } });
    if (path.endsWith('/tests/capabilities')) return route.fulfill({ json: { manage: !failure?.forbidden, readQuestions: true } });
    if (path.includes('/teacher/tests')) {
      calls.push({ method: req.method(), path, body: req.postDataJSON() });
      if (failure?.forbidden) return route.fulfill({ status: 403, json: { code: 'FORBIDDEN', message: 'Không có quyền biên soạn bộ đề.' } });
      if (path.endsWith('/question-units')) return route.fulfill({ json: new URL(req.url()).searchParams.get('partNumber') === '3' ? [unit] : [] });
      if (req.method() === 'POST') { draft = { testId: 't1', versionId: 'v1', version: 1, revision: 0, status: 'DRAFT', document: req.postDataJSON() }; return route.fulfill({ status: 201, json: draft }); }
      if (req.method() === 'PUT') {
        if (failure?.stale) return route.fulfill({ status: 409, json: { code: 'TEST_REVISION_CONFLICT' } });
        draft = { ...draft, revision: draft.revision + 1, document: req.postDataJSON().document }; return route.fulfill({ json: draft });
      }
      if (path.endsWith('/t1')) return route.fulfill({ json: draft });
      return route.fulfill({ json: draft ? [{ testId: 't1', title: draft.document.title, status: 'DRAFT' }] : [] });
    }
    return route.fulfill({ json: {} });
  });
  return calls;
}
test('Teacher creates, edits, reloads a reference draft and removes the whole group', async ({ page }) => {
  const calls = await setup(page);
  await page.goto('/teacher/tests'); await page.getByLabel('Tên bộ đề').fill('Đề nháp');
  await page.getByLabel('Part', { exact: true }).selectOption('3');
  await page.getByRole('button', { name: 'Thêm nguyên mục' }).click();
  await page.getByRole('button', { name: 'Lưu bản nháp' }).click();
  await expect(page.getByText('Đã lưu bản nháp.')).toBeVisible();
  const created = calls.find(c => c.method === 'POST').body;
  expect(created.sections[0].parts[0].questions.map(q => q.questionVersionId)).toEqual(['q3', 'q2']);
  expect(created.durationSeconds).toBeNull(); expect(JSON.stringify(created)).not.toContain('questionText');
  await page.getByLabel('Tên bộ đề').fill('Đề đã sửa'); await page.getByRole('button', { name: 'Lưu bản nháp' }).click();
  await expect(page.getByText('Lần sửa 1', { exact: false })).toBeVisible();
  await page.reload(); await page.getByRole('button', { name: 'Đề đã sửa · DRAFT' }).click();
  await expect(page.getByLabel('Tên bộ đề')).toHaveValue('Đề đã sửa');
  await page.getByRole('button', { name: 'Bỏ mục 1', exact: true }).click();
  await page.getByRole('button', { name: 'Lưu bản nháp' }).click();
  await expect(page.getByText('Lần sửa 2', { exact: false })).toBeVisible();
  expect(calls.filter(c => c.method === 'PUT').at(-1).body.document.sections).toEqual([]);
  expect(calls.some(c => /attempts|publish/.test(c.path))).toBe(false);
});
test('stale revision stops resubmission until explicit reload', async ({ page }) => {
  const calls = await setup(page, { stale: true }); await page.goto('/teacher/tests');
  await page.getByLabel('Tên bộ đề').fill('Draft'); await page.getByRole('button', { name: 'Lưu bản nháp' }).click();
  await expect(page.getByText('Đã lưu bản nháp.')).toBeVisible();
  await page.getByLabel('Tên bộ đề').fill('Local'); await page.getByRole('button', { name: 'Lưu bản nháp' }).click();
  await expect(page.getByRole('alert')).toContainText('Bộ đề đã thay đổi');
  await expect(page.getByRole('button', { name: 'Lưu bản nháp' })).toBeDisabled();
  expect(calls.filter(c => c.method === 'PUT')).toHaveLength(1);
  await page.getByRole('button', { name: 'Đọc lại bản trên máy chủ' }).click();
  await expect(page.getByLabel('Tên bộ đề')).toHaveValue('Draft');
});
test('permission failure shows server message and prevents saving', async ({ page }) => {
  await setup(page, { forbidden: true }); await page.goto('/teacher/tests');
  await expect(page.getByRole('button', { name: 'Lưu bản nháp' })).toBeDisabled();
  await expect(page.getByRole('alert').first()).toContainText('Không có quyền');
});
