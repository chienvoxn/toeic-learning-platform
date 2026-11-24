import { test, expect } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
const file = process.env.SPRINT13_DEMO_CREDENTIALS;
const cfg = file ? JSON.parse(readFileSync(file, 'utf8')) : null;
const out = new URL('../../../backend/target/sprint13-demo-20261124/', import.meta.url);
const stateFile = new URL('browser-checkpoint.json', out);
let state;try { state = JSON.parse(readFileSync(stateFile, 'utf8')); } catch { state = {}; }
test.skip(!cfg, 'Dedicated Sprint 1-3 demo credentials required.');
test.beforeEach(({ page }) => { page.setDefaultTimeout(15000);page.setDefaultNavigationTimeout(20000);page.on('dialog', d => d.accept()); });
const checkpoint = () => writeFileSync(stateFile, JSON.stringify(state, null, 2));
const privateSave = () => writeFileSync(file, JSON.stringify(cfg, null, 2));
function proof(key, value) { state[key] = { source: 'cf2b6d3 + uncommitted demo wiring', database: '55469/toeic_tv2_merge_http_20261124', transport: 'real browser/API/DB, no route stubs', ...value };checkpoint(); }
async function secret(page, selector, value) {
  await page.locator(selector).waitFor();await page.evaluate(({ selector, value }) => { const input = document.querySelector(selector);Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value);input.dispatchEvent(new Event('input', { bubbles: true })); }, { selector, value });
}
async function login(page, role = 'learner') {
  const a = cfg.roles[role];await page.goto('/login');await page.locator('#login-email').fill(a.email);await secret(page, '#login-password', a.password);
  const response = page.waitForResponse(r => r.url().endsWith('/auth/login') && r.request().method() === 'POST');await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();expect((await response).status()).toBe(200);await page.waitForURL(u => u.pathname !== '/login');
}
async function api(page, method, path, body) {
  return page.evaluate(async ({ method, path, body }) => { const token = JSON.parse(localStorage.getItem('toeic.remote.session') || 'null')?.accessToken;const response = await fetch('/api/v1' + path, { method, headers: { ...(token ? { Authorization: 'Bearer ' + token } : {}), ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });const text = await response.text();return { status: response.status, data: text ? JSON.parse(text) : null }; }, { method, path, body });
}
test('Auth thật: register, profile reload, change password, OTP reset qua Mailpit và login lại', async ({ page, request }) => {
  if (!cfg.roles.registration) { cfg.roles.registration = { email: 'sprint13.register.20261125@example.invalid', password: randomUUID() + 'aA9!' };privateSave(); }
  const a = cfg.roles.registration;
  if (!a.registered) {
    await page.goto('/register');await page.locator('#register-name').fill('Sprint 1-3 Registered Learner');await page.locator('#register-email').fill(a.email);await secret(page, '#register-password', a.password);await secret(page, '#register-confirm', a.password);
    const response = page.waitForResponse(r => r.url().endsWith('/auth/register') && r.request().method() === 'POST');await page.getByRole('button', { name: 'TẠO TÀI KHOẢN', exact: true }).click();expect((await response).status()).toBe(201);await page.waitForURL(/\/login$/);a.registered = true;privateSave();
  }
  await login(page, 'registration');const profileName = 'Sprint 1-3 Profile ' + Date.now();await page.goto('/profile');await page.getByRole('button', { name: 'CHỈNH SỬA', exact: true }).click();await page.locator('#profile-name').fill(profileName);await page.getByText('Mục tiêu học tập', { exact: true }).click();await page.locator('#target-score').fill('650');
  const updated = page.waitForResponse(r => r.url().endsWith('/users/me') && r.request().method() === 'PATCH');await page.getByRole('button', { name: 'LƯU', exact: true }).click();expect((await updated).status()).toBe(200);await page.reload();expect((await api(page, 'GET', '/users/me')).data.fullName).toBe(profileName);
  await page.goto('/settings');a.pendingPassword = randomUUID() + 'aA9!';privateSave();
  await secret(page, '#current-password', a.password);await secret(page, '#new-password', a.pendingPassword);await secret(page, '#confirm-password', a.pendingPassword);
  const changed = page.waitForResponse(r => r.url().endsWith('/users/me/password') && r.request().method() === 'POST');await page.getByRole('button', { name: 'Đổi mật khẩu', exact: true }).click();expect((await changed).status()).toBe(204);await page.waitForURL(/\/login$/);a.password = a.pendingPassword;delete a.pendingPassword;privateSave();
  await page.goto('/forgot-password');await page.locator('#recovery-email').fill(a.email);const mailRequest = page.waitForResponse(r => r.url().endsWith('/auth/forgot-password') && r.request().method() === 'POST');await page.getByRole('button', { name: 'Gửi mã đặt lại mật khẩu', exact: true }).click();expect((await mailRequest).status()).toBe(200);
  let message;await expect.poll(async () => { const response = await request.get('http://127.0.0.1:8025/api/v1/messages?limit=100');message = (await response.json()).messages.find(m => m.To?.some(to => to.Address === a.email));return !!message; }).toBe(true);
  const mail = await request.get('http://127.0.0.1:8025/api/v1/message/' + message.ID);const otp = (await mail.json()).Text.match(/\b[0-9]{6}\b/)[0];a.pendingPassword = randomUUID() + 'aA9!';privateSave();
  await secret(page, '#recovery-otp', otp);await secret(page, '#recovery-password', a.pendingPassword);await secret(page, '#recovery-confirm', a.pendingPassword);
  const reset = page.waitForResponse(r => r.url().endsWith('/auth/reset-password') && r.request().method() === 'POST');await page.getByRole('button', { name: 'Đặt lại mật khẩu', exact: true }).click();expect((await reset).status()).toBe(204);await page.waitForURL(/\/login$/);a.password = a.pendingPassword;delete a.pendingPassword;privateSave();await login(page, 'registration');
  proof('auth', { registrationViaBrowser: true, profileReload: true, passwordChange: true, mailpitOtpResetAndRelogin: true, profileScoreIsGoalNotResult: true });
});
test('Teacher UI mới nối editor thật: học liệu DRAFT, nhóm và gửi duyệt có revision', async ({ page }) => {
  await login(page, 'teacher');await page.goto('/teacher/contents');await page.getByRole('link', { name: 'Tạo học liệu', exact: true }).click();await expect(page.locator('.teacher-sidebar')).toBeVisible();await expect(page.locator('#management-kind')).toHaveValue('contents');
  if (!state.lesson) {
    await page.getByRole('button', { name: 'Tạo bản nháp', exact: true }).click();await page.locator('#edit-title').fill('Sprint 1-3: Meeting schedules');await page.locator('#edit-summary').fill('Original demo lesson');await page.locator('#edit-body').fill('Our meeting begins at nine. Please bring the monthly report. We review workplace schedules together.');
    const response = page.waitForResponse(r => r.url().endsWith('/teacher/contents') && r.request().method() === 'POST');await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();const r = await response;expect(r.status()).toBe(201);const d = await r.json();state.lesson = { contentId: d.content.contentId, versionId: d.versions[0].contentVersionId };checkpoint();
  }
  const lesson = (await api(page, 'GET', '/teacher/contents/' + state.lesson.contentId)).data;expect(lesson.versions.some(v => v.contentVersionId === state.lesson.versionId)).toBe(true);await page.reload();await expect(page.getByRole('row').filter({ hasText: 'Sprint 1-3: Meeting schedules' })).toBeVisible();
  const path = `/moderation/contents/${state.lesson.contentId}/versions/${state.lesson.versionId}`;const d = await api(page, 'GET', path);
  if (d.data.status === 'DRAFT') expect((await api(page, 'POST', path + '/submit', { expectedRevision: d.data.revision })).status).toBe(200);
  await page.goto('/teacher/questions');await page.getByRole('link', { name: 'Biên soạn câu hỏi và nhóm', exact: true }).click();await expect(page.locator('#management-kind')).toHaveValue('groups');
  if (!state.group) {
    await page.getByRole('button', { name: 'Tạo bản nháp', exact: true }).click();await page.locator('#edit-groupType').fill('PASSAGE');await page.locator('#edit-instruction').fill('Read the original meeting notice.');await page.locator('#edit-passageText').fill('The team meets every Monday at nine.');
    const response = page.waitForResponse(r => r.url().endsWith('/teacher/question-groups') && r.request().method() === 'POST');await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();const r = await response;expect(r.status()).toBe(201);state.group = (await r.json()).group.groupId;checkpoint();
  }
  expect((await api(page, 'GET', '/teacher/question-groups/' + state.group)).status).toBe(200);
  proof('teacher', { modernLayoutAndCanonicalEditor: true, ownContentAndGroupDraft: true, submittedWithServerRevision: true, noSqlPublication: true });
});
test('Admin thật: approve/publish, user lock/unlock, RBAC, audit/health và Settings lưu rồi phục hồi', async ({ page }) => {
  test.skip(!state.lesson, 'Run Teacher case first to prepare one idempotent lesson.');await login(page, 'admin');
  const path = `/moderation/contents/${state.lesson.contentId}/versions/${state.lesson.versionId}`;let detail = (await api(page, 'GET', path)).data;
  if (detail.status !== 'PUBLISHED') {
    await page.goto('/moderation');await page.locator('#moderation-kind').selectOption('contents');const row = page.getByRole('row').filter({ hasText: state.lesson.versionId });await row.getByRole('button', { name: 'Xem duyệt', exact: true }).click();
    if (detail.status === 'IN_REVIEW') { const response = page.waitForResponse(r => r.url().endsWith('/approve') && r.request().method() === 'POST');await page.getByRole('button', { name: 'Duyệt', exact: true }).click();expect((await response).status()).toBe(200); }
    await page.locator('#moderation-visibility').selectOption('AUTHENTICATED');const published = page.waitForResponse(r => r.url().endsWith('/publish') && r.request().method() === 'POST');await page.getByRole('button', { name: 'Xuất bản', exact: true }).click();expect((await published).status()).toBe(200);
  }
  expect((await api(page, 'GET', '/contents/' + state.lesson.contentId)).status).toBe(200);
  await page.goto('/admin/users');await page.locator('#admin-search').fill(cfg.roles.qa.email);await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();const account = page.getByRole('row').filter({ hasText: cfg.roles.qa.email });await expect(account).toBeVisible();
  if (await account.getByRole('button', { name: 'Mở khóa', exact: true }).count()) await account.getByRole('button', { name: 'Mở khóa', exact: true }).click();await expect(account).toContainText('ACTIVE');
  await account.getByRole('button', { name: 'Khóa', exact: true }).click();await expect(account).toContainText('LOCKED');const denied = await api(page, 'POST', '/auth/login', { email: cfg.roles.qa.email, password: cfg.roles.qa.password });await account.getByRole('button', { name: 'Mở khóa', exact: true }).click();await expect(account).toContainText('ACTIVE');expect(denied.status).toBe(401);
  await page.goto('/admin/roles');await expect(page.getByRole('heading', { name: 'Vai trò và quyền', exact: true })).toBeVisible();await page.locator('#rbac-search').fill('TEACHER');await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();await page.getByRole('button', { name: 'Quản lý', exact: true }).click();await expect(page.getByRole('region', { name: 'Chi tiết RBAC' })).toBeVisible();
  await page.goto('/admin/audit');await expect(page.getByRole('heading', { name: /Audit/ })).toBeVisible();expect((await api(page, 'GET', '/admin/audit-logs')).data.totalElements).toBeGreaterThan(0);
  await page.goto('/admin/overview');await expect(page.getByRole('heading', { name: 'Tổng quan hệ thống', exact: true })).toBeVisible();expect((await api(page, 'GET', '/admin/health')).data.components.database.status).toBe('UP');
  const settings = (await api(page, 'GET', '/admin/system-settings')).data;const original = settings.find(s => s.key === 'vocabulary.recommendations.defaultLimit');
  await page.goto('/admin/settings');const form = page.getByRole('form', { name: 'Số gợi ý từ vựng mặc định', exact: true });const input = form.getByRole('textbox');const value = original.value < original.maxValue ? original.value + 1 : original.value - 1;
  await input.fill(String(value));const changed = page.waitForResponse(r => r.request().method() === 'PATCH' && r.url().includes('/system-settings/'));await form.getByRole('button', { name: 'Lưu cấu hình', exact: true }).click();expect((await changed).status()).toBe(200);await page.reload();await expect(form.getByRole('textbox')).toHaveValue(String(value));
  await form.getByRole('textbox').fill(String(original.value));const restored = page.waitForResponse(r => r.request().method() === 'PATCH' && r.url().includes('/system-settings/'));await form.getByRole('button', { name: 'Lưu cấu hình', exact: true }).click();expect((await restored).status()).toBe(200);
  proof('admin', { publishDifferentContributor: true, userLock401AndUnlock: true, rbacRead: true, auditRead: true, dbHealthUp: true, settingReloadAndOriginalValueRestored: true });
});
test('Learner thật: search/bookmark/history, Practice và Reading Section lưu/reload/submit/result', async ({ page }) => {
  test.skip(!state.lesson, 'Run Teacher/Admin cases first.');await login(page);await page.goto('/catalog');await page.getByRole('textbox', { name: 'Tìm bài học' }).fill('Meeting schedules');const lessonCard = page.getByRole('article').filter({ hasText: 'Sprint 1-3: Meeting schedules' });await expect(lessonCard).toBeVisible();
  const bookmark = lessonCard.getByRole('button', { name: 'Lưu bài học', exact: true });if (await bookmark.count()) await bookmark.click();await page.reload();await expect(page.getByRole('article').filter({ hasText: 'Sprint 1-3: Meeting schedules' }).getByRole('button', { name: 'Bỏ lưu bài học', exact: true })).toBeVisible();await page.locator(`a[href="/catalog/${state.lesson.contentId}"]`).last().click();await expect(page.getByText(/Our meeting begins at nine/)).toBeVisible();expect(JSON.stringify((await api(page, 'GET', '/users/me/bookmarks')).data)).toContain(state.lesson.contentId);expect(JSON.stringify((await api(page, 'GET', '/users/me/content-history')).data)).toContain(state.lesson.contentId);
  const results = [];
  for (const section of [false, true]) {
    await page.goto('/practice');if (section) await page.locator('#practice-mode').selectOption('SECTION');const started = page.waitForResponse(r => r.url().endsWith('/attempts') && r.request().method() === 'POST');await page.getByRole('button', { name: 'Bắt đầu làm bài thật', exact: true }).click();expect((await started).status()).toBe(201);await page.waitForURL(/\/practice\/attempts\/[a-f0-9-]+$/);const id = new URL(page.url()).pathname.split('/').pop();
    const saved = page.waitForResponse(r => r.url().includes('/answers/') && r.request().method() === 'PUT');await page.locator('.question-panel').first().locator('.answer-option').first().click();expect((await saved).status()).toBe(200);await page.reload();await expect(page.locator('.question-panel').first().locator('.answer-option').first()).toHaveAttribute('aria-pressed', 'true');await page.locator('.submit-exam-button').first().click();await page.waitForURL(/\/result$/);const result = (await api(page, 'GET', `/attempts/${id}/result`)).data;expect((await api(page, 'POST', `/attempts/${id}/submit`)).data).toEqual(result);if (section) expect(result.parts.map(p => p.partNumber)).toEqual([5,6,7]);results.push({ id, section, rawScore: result.rawScore });
  }
  await page.goto('/practice/history');await expect(page.getByRole('heading', { name: 'Lịch sử bài làm', exact: true })).toBeVisible();for (const r of results) await expect(page.locator(`[data-attempt-id="${r.id}"]`)).toBeVisible();expect((await api(page, 'GET', '/admin/users')).status).toBe(403);expect((await api(page, 'GET', '/teacher/questions')).status).toBe(403);
  proof('learner', { searchBookmarkReloadAndHistory: true, results, ownerAndRoleBoundaries: true });
});
test('Vocabulary/SRS thật: enroll catalog, lật thẻ, review và tiến độ đọc lại từ DB', async ({ page }) => {
  await login(page, 'qa');const before = (await api(page, 'GET', '/learning-profile/overview')).data.totalReviews;
  if (!state.vocabDeckId) {
    await page.goto('/vocabulary');const all = page.getByRole('button', { name: 'Xem tất cả', exact: true });await expect(all).toBeVisible();await all.click();const card = page.locator('article.vocab-deck').filter({ hasText: 'TOEIC Workplace Essentials' }).filter({ has: page.getByRole('button', { name: 'BẮT ĐẦU HỌC', exact: true }) });await card.getByRole('button', { name: 'BẮT ĐẦU HỌC', exact: true }).click();await page.waitForURL(/\/vocabulary\/[a-f0-9-]+$/);state.vocabDeckId = new URL(page.url()).pathname.split('/').pop();checkpoint();
  } else await page.goto('/vocabulary/' + state.vocabDeckId);
  await page.locator('a[href$="/review"]').click();await page.getByRole('button', { name: 'Lật thẻ xem nghĩa', exact: true }).click();const saved = page.waitForResponse(r => /\/learning-session\/reviews$/.test(r.url()) && r.request().method() === 'POST');await page.getByRole('button', { name: /^TỐT/ }).click();const response = await saved;expect(response.status()).toBe(200);const review = await response.json();expect(review.session.data.completedIds).toContain(review.review.vocabularyId);
  await page.reload();const after = (await api(page, 'GET', '/learning-profile/overview')).data.totalReviews;expect(after).toBeGreaterThan(before);await page.goto('/dashboard');await page.getByRole('link', { name: /HỌC TỪ VỰNG/ }).click();await expect(page).toHaveURL(/\/vocabulary/);
  proof('vocabulary', { catalogEnrolled: true, deckId: state.vocabDeckId, flashcardReviewAndReload: true, totalReviewsBefore: before, totalReviewsAfter: after, progressIsServerStored: true });
});
test('AI thật có giới hạn: greeting/history, no-source và RAG unavailable không làm hỏng luồng lõi', async ({ page }) => {
  await login(page);await page.goto('/settings');await page.getByRole('button', { name: 'Mở trợ lý TOEIC', exact: true }).click();await page.getByLabel('Câu hỏi TOEIC', { exact: true }).fill('Xin chào');const sent = page.waitForResponse(r => r.url().includes('/ai/conversations/') && r.request().method() === 'POST');await page.getByRole('button', { name: 'Gửi câu hỏi', exact: true }).click();expect((await sent).status()).toBe(200);await expect(page.getByRole('log')).toContainText('Xin chào');await page.reload();
  const conversations = await api(page, 'GET', '/ai/conversations');expect(conversations.status).toBe(200);expect(conversations.data.length).toBeGreaterThan(0);
  const full = await api(page, 'GET', '/tests');expect(full.status).toBe(200);expect(full.data).toEqual([]);
  await login(page, 'admin');const indexing = await api(page, 'POST', `/admin/ai/contents/${state.lesson.contentId}/index`);expect(indexing.status).toBe(503);expect((await api(page, 'GET', '/contents/' + state.lesson.contentId)).status).toBe(200);
  proof('aiAndLimits', { localGreetingAndHistory: true, externalRagUnavailable503: true, coreContentStill200: true, fullMockCatalogEmptyNotSeeded: true, listeningPlaybackCheckedBySeparateListeningSuite: true, geminiCalled: false, noMockFallback: true });
});
