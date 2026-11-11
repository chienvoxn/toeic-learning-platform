// Kiểm thử local qua proxy Vite. Mật khẩu, token và OTP chỉ giữ trong bộ nhớ.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';


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
  assert.equal(registered.status, 'ACTIVE');
  console.log(`PASS: Đăng ký tài khoản ${email}, trạng thái ACTIVE, không cần OTP.`);
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
