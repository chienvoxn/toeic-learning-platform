import { apiRequest } from '../../../services/apiClient.js';
import { toClientUser } from '../api/auth.mapper.js';
import { readSession, saveSession, clearSession } from '../session.js';

export class RemoteAuthRepository {
  async login({ email, password }) {
    const data = await apiRequest('/auth/login', { method: 'POST', auth: false, body: { email, password } });
    saveSession(data);
    try { return { user: await this.getCurrentUser() }; }
    catch (error) { clearSession(data.accessToken); throw error; }
  }

  register({ email, password, fullName }) {
    return apiRequest('/auth/register', { method: 'POST', auth: false, body: { email, password, fullName } });
  }

  verifyEmail({ email, otp }) {
    return apiRequest('/auth/verify-email', { method: 'POST', auth: false, body: { email, otp } });
  }

  resendOtp(email) {
    return apiRequest('/auth/resend-otp', { method: 'POST', auth: false, body: { email } });
  }

  async getCurrentUser() {
    const session = readSession();
    if (!session) return null;
    if (session.expiresAt <= Date.now()) { clearSession(session.accessToken); return null; }
    return toClientUser(await apiRequest('/users/me', { token: session.accessToken }));
  }

  async logout() {
    const token = readSession()?.accessToken;
    try { if (token) await apiRequest('/auth/logout', { method: 'POST', token }); }
    finally { clearSession(token); }
  }

  async updateProfile(updates) {
    const body = {};
    for (const key of ['fullName', 'targetScore']) {
      if (Object.hasOwn(updates, key) && updates[key] !== undefined) body[key] = updates[key];
    }
    return toClientUser(await apiRequest('/users/me', { method: 'PATCH', body }));
  }

  async changePassword({ currentPassword, newPassword }) {
    const token = readSession()?.accessToken;
    await apiRequest('/users/me/password', { method: 'POST', token, body: { currentPassword, newPassword } });
    clearSession(token);
  }
}
