import { apiRequest } from '../../../services/apiClient.js';
import { toClientUser } from '../api/auth.mapper.js';
import { readSession, saveSession, clearSession } from '../session.js';

export class RemoteAuthRepository {
  async login({ email, password }) {
    const data = await apiRequest('/auth/login', { method: 'POST', auth: false, body: { email, password } });
    return this.startSession(data);
  }

  register({ email, password, fullName }) {
    return apiRequest('/auth/register', { method: 'POST', auth: false, body: { email, password, fullName } });
  }

  forgotPassword(email) {
    return apiRequest('/auth/forgot-password', { method: 'POST', auth: false, body: { email } });
  }

  resetPassword(email, otp, newPassword) {
    return apiRequest('/auth/reset-password', { method: 'POST', auth: false, body: { email, otp, newPassword } });
  }

  googleChallenge() {
    return apiRequest('/auth/google/challenge', { method: 'POST', auth: false, credentials: 'same-origin' });
  }

  async loginWithGoogle({ credential, challengeId, currentPassword }) {
    const data = await apiRequest('/auth/google', { method: 'POST', auth: false, credentials: 'same-origin',
      body: { credential, challengeId, ...(currentPassword !== undefined ? { currentPassword } : {}) } });
    return this.startSession(data);
  }

  async startSession(data) {
    saveSession(data);
    try { return { user: await this.getCurrentUser() }; }
    catch (error) { clearSession(data.accessToken); throw error; }
  }

  async getCurrentUser() {
    const session = readSession();
    if (!session) return null;
    if (session.expiresAt <= Date.now()) { clearSession(session.accessToken); return null; }
    const user = await apiRequest('/users/me', { token: session.accessToken });
    // A successful response can also arrive after logout or a different login.
    return readSession()?.accessToken === session.accessToken ? toClientUser(user) : null;
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

  async changePassword(currentPassword, newPassword) {
    if (typeof currentPassword === 'object') ({ currentPassword, newPassword } = currentPassword);
    const token = readSession()?.accessToken;
    await apiRequest('/users/me/password', { method: 'POST', token, body: { currentPassword, newPassword } });
    clearSession(token);
  }
}
