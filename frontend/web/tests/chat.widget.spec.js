import { test, expect } from '@playwright/test';

test('deleted guard conversation stays locked until a new owned server check succeeds (HTTP fixtures)', async ({ page }) => {
  const user = { userId: 'guard-browser-user', email: 'guard@example.test', fullName: 'Guard Learner', status: 'ACTIVE', roles: ['LEARNER'] };
  let deleted = false;
  await page.addInitScript(() => {
    localStorage.setItem('toeic.remote.session', JSON.stringify({ accessToken: 'browser-fixture-token', tokenType: 'Bearer', expiresAt: Date.now() + 3600000 }));
    localStorage.setItem('toeic.active.exam', JSON.stringify({ version: 1, attempts: [], restrictions: [{ userId: 'guard-browser-user', conversationId: 'deleted' }] }));
  });
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/v1/users/me') return route.fulfill({ json: user });
    if (path === '/api/v1/ai/conversations/deleted/messages') { deleted = true;return route.fulfill({ status: 404, json: { code: 'NOT_FOUND' } }); }
    if (path === '/api/v1/ai/conversations') return route.fulfill({ json: route.request().method() === 'POST' ? { id: 'fresh', title: 'Owned conversation' } : [] });
    if (path === '/api/v1/ai/conversations/fresh/messages') return route.fulfill({ json: [] });
    return route.fulfill({ json: {} });
  });
  await page.goto('/settings');await page.getByRole('button', { name: 'Mở trợ lý TOEIC', exact: true }).click();
  await expect(page.getByLabel('Câu hỏi TOEIC', { exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Kiểm tra lại trạng thái thi', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Hội thoại kiểm tra');expect(deleted).toBe(true);
  await expect(page.getByLabel('Câu hỏi TOEIC', { exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Kiểm tra lại trạng thái thi', exact: true }).click();
  await expect(page.getByLabel('Câu hỏi TOEIC', { exact: true })).toBeEnabled();
});

// This suite verifies browser behavior using explicit HTTP fixtures, not live Gemini.
test('chat bubble sends, shows sources, reopens history and fits mobile', async ({ page }) => {
  const user = { userId: 'chat-browser-user', email: 'chat@example.test', fullName: 'Chat Learner', status: 'ACTIVE', roles: ['LEARNER'] };
  const conversation = { id: 'conversation-1', title: 'Present perfect', updatedAt: new Date().toISOString() };
  const answer = { answer: 'Use have or has with the past participle.', grounded: true, warning: null,
    citations: [{ documentId: 'doc', chunkId: 'chunk', contentId: 'grammar', contentVersionId: 'version-1', title: 'Present perfect guide', excerpt: 'Have or has + past participle.', page: null }] };
  let sent = false;
  let requestId;
  let askCount = 0;
  let deleted = false;
  await page.addInitScript(() => localStorage.setItem('toeic.remote.session', JSON.stringify({ accessToken: 'browser-fixture-token', tokenType: 'Bearer', expiresAt: Date.now() + 3600000 })));
  await page.route('**/api/v1/**', async route => {
    const { pathname } = new URL(route.request().url());
    let body = {};
    if (pathname === '/api/v1/users/me') body = user;
    else if (pathname === '/api/v1/ai/conversations') body = route.request().method() === 'POST' ? conversation : (sent && !deleted ? [conversation] : []);
    else if (pathname === `/api/v1/ai/conversations/${conversation.id}` && route.request().method() === 'DELETE') {
      deleted = true; return route.fulfill({ status: 204, body: '' });
    }
    else if (pathname.endsWith('/messages')) {
      if (route.request().method() === 'POST') {
        const payload = route.request().postDataJSON();
        expect(payload.question).toBe('Explain present perfect'); expect(payload.requestId).toBeTruthy();
        expect(Object.keys(payload).sort()).toEqual(['question', 'requestId']); sent = true; body = answer;
        askCount++;
        if (askCount === 1) { requestId = payload.requestId; return route.abort('failed'); }
        expect(payload.requestId).toBe(requestId);
      } else body = sent ? [{ id: 'u1', role: 'user', content: 'Explain present perfect' }, { id: 'a1', role: 'assistant', content: answer.answer, answer }] : [];
    }
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  });
  await page.goto('/settings');
  await page.getByRole('button', { name: 'Mở trợ lý TOEIC', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'Trợ lý TOEIC', exact: true })).toBeVisible();
  await page.getByLabel('Câu hỏi TOEIC', { exact: true }).fill('Explain present perfect');
  await page.getByRole('button', { name: 'Gửi câu hỏi', exact: true }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await page.getByRole('button', { name: 'Gửi câu hỏi', exact: true }).click();
  await expect(page.getByText(answer.answer, { exact: true })).toBeVisible();
  await page.getByText('Nguồn tham khảo (1)', { exact: true }).click();
  await expect(page.getByRole('link', { name: 'Present perfect guide', exact: true })).toHaveAttribute('href', '/catalog/grammar');
  await page.screenshot({ path: 'test-results/chat-desktop.png' });
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('button', { name: 'Mở trợ lý TOEIC', exact: true }).click();
  await expect(page.getByText(answer.answer, { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Hội thoại', exact: true }).click();
  page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: `Xóa hội thoại ${conversation.title}`, exact: true }).click();
  await expect(page.getByRole('button', { name: `Xóa hội thoại ${conversation.title}`, exact: true })).toHaveCount(0);
  const bounds = await page.getByRole('dialog').boundingBox();
  expect(bounds.x).toBeGreaterThanOrEqual(0); expect(bounds.x + bounds.width).toBeLessThanOrEqual(390);
  await page.screenshot({ path: 'test-results/chat-mobile.png' });
});
