import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';

const mailBase = process.env.WEB_AUTH_MAIL_URL || 'http://127.0.0.1:8025';
const sessionKey = 'toeic.remote.session';

test.beforeEach(async ({ page }) => {
  page.setDefaultTimeout(15_000);
  page.on('pageerror', error => console.log('Browser error:', error.name, error.message));
  // Log only route/status diagnostics, never headers, request bodies or credentials.
  page.on('requestfailed', request => {
    if (request.url().includes('/api/')) console.log('HTTP request failed:', new URL(request.url()).pathname, request.failure()?.errorText);
  });
});

test.afterEach(async ({ page }) => {
  await page.evaluate(() => {
    document.querySelectorAll('input[type="password"], input[autocomplete="one-time-code"]').forEach(input => { input.value = ''; });
  }).catch(() => {});
});

// Avoid Playwright call logs containing password/OTP values, including on failure.
async function secretInput(page, selector, value) {
  await page.locator(selector).waitFor({ state: 'visible' });
  await page.evaluate(({ selector, value }) => {
    const input = document.querySelector(selector);
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }, { selector, value });
}
async function login(page, email, password, expectedStatus = 200) {
  console.log('Login checkpoint; expected HTTP', expectedStatus);
  await page.goto('/login');
  await page.locator('#login-email').fill(email);
  await secretInput(page, '#login-password', password);
  const response = page.waitForResponse(item => item.url().endsWith('/auth/login') && item.request().method() === 'POST', { timeout: 15_000 });
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  expect((await response).status()).toBe(expectedStatus);
  console.log('Login response received; HTTP', expectedStatus);
}
async function latestMail(request, email) {
  let message;
  await expect.poll(async () => {
    const response = await request.get(mailBase + '/api/v1/messages?limit=100');
    if (!response.ok()) return false;
    const data = await response.json();
    message = data.messages.find(item => item.To?.some(to => to.Address === email));
    return !!message;
  }).toBe(true);
  const response = await request.get(mailBase + '/api/v1/message/' + message.ID);
  expect(response.status()).toBe(200);
  const data = await response.json();
  const otp = data.Text?.match(/\b[0-9]{6}\b/)?.[0];
  expect(!!otp).toBe(true);
  return { otp, id: message.ID };
}
async function register(page) {
  const email = 'web-auth-' + randomUUID() + '@example.test';
  const password = randomUUID() + 'aA9!';
  await page.goto('/register');
  await page.locator('#register-name').fill('Web Auth Integration');
  await page.locator('#register-email').fill(email);
  await secretInput(page, '#register-password', password);
  await secretInput(page, '#register-confirm', password);
  await page.getByRole('button', { name: 'TẠO TÀI KHOẢN', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect(await page.evaluate(key => localStorage.getItem(key) === null, sessionKey)).toBe(true);
  return { email, password };
}

test('Identity thật: đăng ký ACTIVE, login, hồ sơ, mật khẩu và logout', async ({ page }) => {
  await page.goto('/profile');
  await expect(page).toHaveURL(/\/login$/);
  const { email, password } = await register(page);
  await expect(page.getByRole('status')).toContainText('không cần OTP');
  await login(page, email, randomUUID(), 401);
  await expect(page.getByRole('alert').filter({ hasText: 'Email hoặc mật khẩu' })).toBeVisible();
  expect(await page.evaluate(key => localStorage.getItem(key) === null, sessionKey)).toBe(true);
  await login(page, email, password);
  await expect(page).toHaveURL(/\/dashboard$/);
  await page.goto('/profile');
  await expect(page.getByRole('heading', { name: 'Web Auth Integration', exact: true })).toBeVisible();
  await expect(page.getByText('Chưa đặt mục tiêu', { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Web Auth Integration', exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'CHỈNH SỬA', exact: true }).click();
  await page.locator('details.profile-target').evaluate(element => { element.open = true; });
  await page.locator('#profile-name').fill('Web Profile Saved');
  await page.locator('#target-score').fill('750');
  await page.getByRole('button', { name: 'LƯU', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Đã lưu hồ sơ');
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Web Profile Saved', exact: true })).toBeVisible();
  await expect(page.getByText('750', { exact: true })).toBeVisible();

  // Name-only edit must omit targetScore, preserving the persisted goal.
  await page.getByRole('button', { name: 'CHỈNH SỬA', exact: true }).click();
  await page.locator('details.profile-target').evaluate(element => { element.open = true; });
  await page.locator('#profile-name').fill('Web Name Only');
  const patch = page.waitForRequest(req => req.method() === 'PATCH' && req.url().endsWith('/users/me'));
  await page.getByRole('button', { name: 'LƯU', exact: true }).click();
  expect(Object.hasOwn((await patch).postDataJSON(), 'targetScore')).toBe(false);
  await expect(page.getByRole('status')).toContainText('Đã lưu hồ sơ');
  await expect(page.getByText('750', { exact: true })).toBeVisible();

  // Whitespace-only name reaches backend validation and cannot partially change goal.
  await page.getByRole('button', { name: 'CHỈNH SỬA', exact: true }).click();
  await page.locator('details.profile-target').evaluate(element => { element.open = true; });
  await page.locator('#profile-name').fill('   ');
  await page.locator('#target-score').fill('800');
  await page.getByRole('button', { name: 'LƯU', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Dữ liệu không hợp lệ');
  await page.getByRole('button', { name: 'Hủy chỉnh sửa' }).click();
  await expect(page.getByText('750', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'CHỈNH SỬA', exact: true }).click();
  await page.locator('details.profile-target').evaluate(element => { element.open = true; });
  await page.locator('#target-score').fill('');
  const clear = page.waitForRequest(req => req.method() === 'PATCH' && req.url().endsWith('/users/me'));
  await page.getByRole('button', { name: 'LƯU', exact: true }).click();
  const clearBody = (await clear).postDataJSON();
  expect(clearBody.targetScore === null && !Object.hasOwn(clearBody, 'fullName')).toBe(true);
  await expect(page.getByRole('status')).toContainText('Đã lưu hồ sơ');
  await page.reload();
  await expect(page.getByText('Chưa đặt mục tiêu', { exact: true })).toBeVisible();

  const oldSession = await page.evaluate(key => JSON.parse(localStorage.getItem(key)).accessToken, sessionKey);
  const secondSession = await page.evaluate(async ({ email, password }) => {
    const response = await fetch('/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) });
    const body = await response.json(); return { status: response.status, token: body.accessToken };
  }, { email, password });
  expect(secondSession.status).toBe(200);
  const newPassword = randomUUID() + 'nN8!';
  await page.goto('/settings');
  await secretInput(page, '#current-password', randomUUID());
  await secretInput(page, '#new-password', newPassword);
  await secretInput(page, '#confirm-password', newPassword);
  await page.getByRole('button', { name: 'Đổi mật khẩu', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Phiên đăng nhập vẫn được giữ');
  await expect(page).toHaveURL(/\/settings$/);
  expect(await page.evaluate(key => !!localStorage.getItem(key), sessionKey)).toBe(true);

  await secretInput(page, '#current-password', password);
  await secretInput(page, '#new-password', newPassword);
  await secretInput(page, '#confirm-password', newPassword);
  await page.getByRole('button', { name: 'Đổi mật khẩu', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect(await page.evaluate(key => localStorage.getItem(key) === null, sessionKey)).toBe(true);
  for (const token of [oldSession, secondSession.token]) {
    const status = await page.evaluate(async token => (await fetch('/api/v1/users/me', { headers: { Authorization: 'Bearer ' + token } })).status, token);
    expect(status).toBe(401);
  }
  await login(page, email, password, 401);
  await expect(page.getByRole('alert').filter({ hasText: 'Email hoặc mật khẩu' })).toBeVisible();
  await login(page, email, newPassword);
  // The protected Settings route is retained as the post-login destination.
  await expect(page).toHaveURL(/\/settings$/);
  const logoutToken = await page.evaluate(key => JSON.parse(localStorage.getItem(key)).accessToken, sessionKey);
  await page.locator('.sidebar-link.logout-link').click();
  await expect(page).toHaveURL(/\/login$/);
  const revoked = await page.evaluate(async token => (await fetch('/api/v1/users/me', { headers: { Authorization: 'Bearer ' + token } })).status, logoutToken);
  expect(revoked).toBe(401);
  await page.goto('/profile');
  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/catalog');
  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/tests');
  await expect(page).toHaveURL(/\/tests$/);
});

test('Khôi phục qua SMTP/OTP thật, cooldown, OTP hết hạn và thu hồi phiên', async ({ page, request }) => {
  const { email, password } = await register(page);
  await login(page, email, password);
  await expect(page).toHaveURL(/\/dashboard$/);
  const token = await page.evaluate(key => JSON.parse(localStorage.getItem(key)).accessToken, sessionKey);
  await page.goto('/forgot-password');
  await page.locator('#recovery-email').fill(email);
  await page.getByRole('button', { name: 'Gửi mã đặt lại mật khẩu', exact: true }).click();
  const initial = await latestMail(request, email);
  await expect(page.getByRole('button', { name: /Gửi lại sau/ })).toBeDisabled();
  const nextPassword = randomUUID() + 'nN9!';
  await secretInput(page, '#recovery-otp', initial.otp === '000000' ? '000001' : '000000');
  await secretInput(page, '#recovery-password', nextPassword);
  await secretInput(page, '#recovery-confirm', nextPassword);
  await page.getByRole('button', { name: 'Đặt lại mật khẩu', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Email hoặc mã OTP');
  const database = process.env.WEB_AUTH_FIXTURE_DB;
  if (database !== 'toeic_web_auth_sync_http_test' || !process.env.WEB_AUTH_PSQL) {
    throw new Error('Requires isolated toeic_web_auth_sync_http_test and WEB_AUTH_PSQL.');
  }
  try { execFileSync(process.env.WEB_AUTH_PSQL, ['-h', '127.0.0.1', '-p', '55459', '-U', 'q03_main_test', '-d', database, '-v', 'ON_ERROR_STOP=1',
    '-c', "UPDATE password_reset_otps SET created_at=now()-interval '10 minutes',expires_at=now()-interval '1 second' WHERE user_id=(SELECT user_id FROM users WHERE email='" + email + "');"], { stdio: 'pipe' }); }
  catch { throw new Error('Could not expire isolated reset fixture without exposing row data.'); }
  await secretInput(page, '#recovery-otp', initial.otp);
  await page.getByRole('button', { name: 'Đặt lại mật khẩu', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('hết hạn');
  // Wait for the browser's real cooldown. No response interception or production DB changes.
  const resend = page.getByRole('button', { name: 'Gửi lại mã OTP', exact: true });
  await expect(resend).toBeEnabled({ timeout: 70_000 });
  await resend.click();
  let resent;
  await expect.poll(async () => { resent = await latestMail(request, email); return resent.id !== initial.id; }).toBe(true);
  await secretInput(page, '#recovery-otp', resent.otp);
  await secretInput(page, '#recovery-password', nextPassword);
  await secretInput(page, '#recovery-confirm', nextPassword);
  await page.getByRole('button', { name: 'Đặt lại mật khẩu', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect(await page.evaluate(key => localStorage.getItem(key) === null, sessionKey)).toBe(true);
  const revoked = await page.evaluate(async token => (await fetch('/api/v1/users/me', { headers: { Authorization: 'Bearer ' + token } })).status, token);
  expect(revoked).toBe(401);
  await login(page, email, password, 401);
  await expect(page.getByRole('alert').filter({ hasText: 'Email hoặc mật khẩu' })).toBeVisible();
  await login(page, email, nextPassword);
  await expect(page).toHaveURL(/\/dashboard$/);
});

test('Phiên sai bị xóa qua 401 thật, không khôi phục demo user', async ({ page }) => {
  await page.addInitScript(key => {
    localStorage.setItem(key, JSON.stringify({ accessToken: crypto.randomUUID(), tokenType: 'Bearer', expiresAt: Date.now() + 60_000 }));
    localStorage.setItem('auth_user', JSON.stringify({ fullName: 'Fake Cached User' }));
  }, sessionKey);
  await page.goto('/profile');
  await expect(page).toHaveURL(/\/login$/);
  expect(await page.evaluate(key => localStorage.getItem(key) === null, sessionKey)).toBe(true);
  await expect(page.getByRole('link', { name: 'Quên mật khẩu?' })).toBeVisible();
});

test('Mất mạng không fallback demo; giữ token để retry GET me', async ({ page, context }) => {
  const { email, password } = await register(page);
  await login(page, email, password);
  await expect(page).toHaveURL(/\/dashboard$/);
  await page.goto('/profile');
  await context.setOffline(true);
  await page.evaluate(key => window.dispatchEvent(new StorageEvent('storage', { key })), sessionKey);
  await expect(page.getByRole('alert')).toContainText('Không thể kết nối');
  await expect(page.getByRole('heading', { name: 'Web Auth Integration', exact: true })).toHaveCount(0);
  expect(await page.evaluate(key => !!localStorage.getItem(key), sessionKey)).toBe(true);
  await context.setOffline(false);
  await page.getByRole('button', { name: 'Thử lại', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Web Auth Integration', exact: true })).toBeVisible();
  await page.locator('.sidebar-link.logout-link').click();
  await expect(page).toHaveURL(/\/login$/);
});
