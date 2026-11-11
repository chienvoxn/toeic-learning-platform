import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';

const mailBase = process.env.WEB_AUTH_MAIL_URL || 'http://127.0.0.1:8025';
const sessionKey = 'toeic.remote.session';

test.afterEach(async ({ page }) => {
  await page.evaluate(() => {
    document.querySelectorAll('input[type="password"], input[autocomplete="one-time-code"]').forEach(input => { input.value = ''; });
  }).catch(() => {});
});

// Avoid Playwright call logs containing password/OTP values, including on failure.
async function secretInput(page, selector, value) {
  await page.evaluate(({ selector, value }) => {
    const input = document.querySelector(selector);
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  }, { selector, value });
}
async function login(page, email, password) {
  await page.goto('/login');
  await page.locator('#login-email').fill(email);
  await secretInput(page, '#login-password', password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
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
  await page.getByRole('button', { name: 'TẠO TÀI KHOẢN', exact: true }).click();
  await expect(page).toHaveURL(/\/verify-email$/);
  expect(await page.evaluate(key => localStorage.getItem(key) === null, sessionKey)).toBe(true);
  return { email, password };
}

test('Identity thật: đăng ký, SMTP/OTP/resend, login, hồ sơ, mật khẩu và logout', async ({ page, request }) => {
  await page.goto('/profile');
  await expect(page).toHaveURL(/\/login$/);
  const { email, password } = await register(page);
  const initial = await latestMail(request, email);
  await page.reload();
  await expect(page.locator('#verify-email')).toHaveValue(email);

  const wrongOtp = initial.otp === '000000' ? '000001' : '000000';
  await secretInput(page, '#verify-otp', wrongOtp);
  await page.getByRole('button', { name: 'Xác minh email', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('không đúng');

  await page.getByRole('button', { name: 'Gửi lại OTP' }).click();
  await expect(page.getByRole('alert')).toContainText('quá sớm');
  // Use the real 60-second server cooldown, without mocking a response.
  await expect.poll(async () => {
    const response = page.waitForResponse(item => item.url().endsWith('/auth/resend-otp') && item.request().method() === 'POST');
    await page.getByRole('button', { name: 'Gửi lại OTP' }).click();
    return (await response).status();
  }, { timeout: 75_000, intervals: [5000] }).toBe(200);
  const resent = await latestMail(request, email);
  expect(resent.id !== initial.id).toBe(true);
  await secretInput(page, '#verify-otp', resent.otp);
  await page.getByRole('button', { name: 'Xác minh email', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('status')).toContainText('Email đã xác minh');

  await login(page, email, randomUUID());
  await expect(page.getByRole('alert')).toContainText('Email hoặc mật khẩu');
  expect(await page.evaluate(key => localStorage.getItem(key) === null, sessionKey)).toBe(true);
  await login(page, email, password);
  await expect(page).toHaveURL(/\/profile$/);
  await expect(page.getByRole('heading', { name: 'Web Auth Integration', exact: true })).toBeVisible();
  await expect(page.getByText('Chưa đặt mục tiêu', { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Web Auth Integration', exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'CHỈNH SỬA', exact: true }).click();
  await page.locator('#profile-name').fill('Web Profile Saved');
  await page.locator('#target-score').fill('750');
  await page.getByRole('button', { name: 'LƯU', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Đã lưu hồ sơ');
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Web Profile Saved', exact: true })).toBeVisible();
  await expect(page.getByText('750', { exact: true })).toBeVisible();

  // Name-only edit must omit targetScore, preserving the persisted goal.
  await page.getByRole('button', { name: 'CHỈNH SỬA', exact: true }).click();
  await page.locator('#profile-name').fill('Web Name Only');
  const patch = page.waitForRequest(req => req.method() === 'PATCH' && req.url().endsWith('/users/me'));
  await page.getByRole('button', { name: 'LƯU', exact: true }).click();
  expect(Object.hasOwn((await patch).postDataJSON(), 'targetScore')).toBe(false);
  await expect(page.getByRole('status')).toContainText('Đã lưu hồ sơ');
  await expect(page.getByText('750', { exact: true })).toBeVisible();

  // Whitespace-only name reaches backend validation and cannot partially change goal.
  await page.getByRole('button', { name: 'CHỈNH SỬA', exact: true }).click();
  await page.locator('#profile-name').fill('   ');
  await page.locator('#target-score').fill('800');
  await page.getByRole('button', { name: 'LƯU', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Dữ liệu không hợp lệ');
  await page.getByRole('button', { name: 'Hủy chỉnh sửa' }).click();
  await expect(page.getByText('750', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'CHỈNH SỬA', exact: true }).click();
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
  await secretInput(page, '#current-password', randomUUID());
  await secretInput(page, '#new-password', newPassword);
  await secretInput(page, '#confirm-password', newPassword);
  await page.getByRole('button', { name: 'Đổi mật khẩu', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Phiên đăng nhập vẫn được giữ');
  await expect(page).toHaveURL(/\/profile$/);
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
  await login(page, email, password);
  await expect(page.getByRole('alert')).toContainText('Email hoặc mật khẩu');
  await login(page, email, newPassword);
  await expect(page).toHaveURL(/\/profile$/);
  const logoutToken = await page.evaluate(key => JSON.parse(localStorage.getItem(key)).accessToken, sessionKey);
  await page.locator('.sidebar-link.logout-link').click();
  await expect(page).toHaveURL(/\/login$/);
  const revoked = await page.evaluate(async token => (await fetch('/api/v1/users/me', { headers: { Authorization: 'Bearer ' + token } })).status, logoutToken);
  expect(revoked).toBe(401);
  await page.goto('/profile');
  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/catalog');
  await expect(page.getByText(/DEMO — học liệu/)).toBeVisible();
});

test('OTP hết hạn thật bằng fixture trên DB kiểm thử riêng', async ({ page, request }) => {
  const { email } = await register(page);
  const mail = await latestMail(request, email);
  const database = process.env.WEB_AUTH_FIXTURE_DB;
  if (database !== 'toeic_web_auth_test' || !process.env.WEB_AUTH_PSQL) {
    throw new Error('Requires isolated toeic_web_auth_test and WEB_AUTH_PSQL; never run fixture against a personal DB.');
  }
  // Only this newly registered fixture account; no reset/delete and no credential in SQL.
  try { execFileSync(process.env.WEB_AUTH_PSQL, ['-h', '127.0.0.1', '-p', '55439', '-U', 'auth_rbac_test', '-d', database, '-v', 'ON_ERROR_STOP=1',
    '-c', "UPDATE email_verification_otps SET created_at=now()-interval '10 minutes',expires_at=now()-interval '1 second' WHERE user_id=(SELECT user_id FROM users WHERE email='" + email + "');"], { stdio: 'pipe' }); }
  catch { throw new Error('Could not expire the isolated OTP fixture; inspect local DB setup without printing row data.'); }
  await secretInput(page, '#verify-otp', mail.otp);
  await page.getByRole('button', { name: 'Xác minh email', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('hết hạn');
});

test('Phiên sai bị xóa qua 401 thật, không khôi phục demo user', async ({ page }) => {
  await page.addInitScript(key => {
    localStorage.setItem(key, JSON.stringify({ accessToken: crypto.randomUUID(), tokenType: 'Bearer', expiresAt: Date.now() + 60_000 }));
    localStorage.setItem('auth_user', JSON.stringify({ fullName: 'Fake Cached User' }));
  }, sessionKey);
  await page.goto('/profile');
  await expect(page).toHaveURL(/\/login$/);
  expect(await page.evaluate(key => localStorage.getItem(key) === null, sessionKey)).toBe(true);
  await expect(page.getByRole('button', { name: /Google login/ })).toBeDisabled();
});

test('Mất mạng không fallback demo; giữ token để retry GET me', async ({ page, context, request }) => {
  const { email, password } = await register(page);
  const { otp } = await latestMail(request, email);
  await secretInput(page, '#verify-otp', otp);
  await page.getByRole('button', { name: 'Xác minh email', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  await login(page, email, password);
  await expect(page).toHaveURL(/\/profile$/);
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
