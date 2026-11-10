// Kiểm thử local qua proxy Vite. Mật khẩu, token và OTP chỉ giữ trong bộ nhớ.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';

const base = 'http://localhost:5173';
const email = `toeic.smoke.${Date.now()}@example.com`;
const password = randomUUID();
async function request(path, { body, token, method = 'GET', expected = 200 } = {}) {
  const response = await fetch(`${base}${path}`, {
    method, signal: AbortSignal.timeout(15000),
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  // Chỉ báo endpoint/status khi lỗi, không dump body có thể chứa token.
  assert.equal(response.status, expected, `${method} ${path}: HTTP ${response.status}`);
  return response.status === 204 ? null : response.json();
}
try {
  assert.equal((await request('/api/health/database')).status, 'UP');
  console.log('PASS: Database qua proxy frontend kết nối thành công.');
  const registered = await request('/api/v1/auth/register', { method: 'POST', expected: 201, body: { email, password, fullName: 'Học viên kiểm thử local' } });
  assert.equal(registered.status, 'PENDING');
  console.log(`PASS: Đăng ký tài khoản ${email}, trạng thái PENDING.`);
  await request('/api/v1/auth/login', { method: 'POST', expected: 401, body: { email, password } });
  console.log('PASS: Tài khoản chưa xác minh không đăng nhập được.');
  let mail;
  for (let attempt = 0; attempt < 20 && !mail; attempt++) {
    const response = await fetch('http://127.0.0.1:8025/api/messages', { signal: AbortSignal.timeout(5000) });
    const messages = await response.json();
    mail = messages.find(entry => entry.recipients.some(recipient => recipient.includes(email)));
    if (!mail) await delay(500);
  }
  assert.ok(mail, 'Chưa nhận được email OTP trong hộp thư local.');
  const rawBody = mail.raw.split('\r\n\r\n').slice(1).join('\r\n\r\n');
  const text = /Content-Transfer-Encoding:\s*base64/i.test(mail.raw.split('\r\n\r\n')[0]) ? Buffer.from(rawBody, 'base64').toString('utf8') : rawBody;
  const otp = text.match(/\b\d{6}\b/)?.[0];
  assert.ok(otp, 'Email chưa chứa OTP hợp lệ.');
  await request('/api/v1/auth/verify-email', { method: 'POST', body: { email, otp } });
  console.log('PASS: Nhận thư và xác minh OTP thành công.');
  const session = await request('/api/v1/auth/login', { method: 'POST', body: { email, password } });
  assert.ok(session.accessToken); assert.equal(session.user.status, 'ACTIVE');
  const me = await request('/api/v1/users/me', { token: session.accessToken });
  assert.equal(me.email, email);
  console.log('PASS: Đăng nhập và đọc hồ sơ ACTIVE từ database.');
  await request('/api/v1/auth/logout', { method: 'POST', token: session.accessToken, expected: 204 });
  await request('/api/v1/users/me', { token: session.accessToken, expected: 401 });
  console.log('PASS: Đăng xuất thu hồi phiên trên backend.');
  console.log('Tài khoản trên chỉ dành cho smoke test. Hãy tạo tài khoản riêng trên trang đăng ký để sử dụng.');
} catch (error) {
  console.error(`FAIL: ${error.message}`); process.exitCode = 1;
}
