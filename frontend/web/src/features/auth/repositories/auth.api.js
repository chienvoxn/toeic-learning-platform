import { apiRequest } from '@/services/apiClient';
import { toClientUser } from '../api/auth.mapper';

export class RemoteAuthRepository {
  async login({ email, password }) {
    const data = await apiRequest('/auth/login', {
      method: 'POST',
      token: null,
      body: { email, password },
    });
    localStorage.setItem('auth_token', data.accessToken);
    const user = toClientUser(data.user);
    localStorage.setItem('auth_user', JSON.stringify(user));
    return { user, token: data.accessToken };
  }

  async register(payload) {
    const data = await apiRequest('/auth/register', {
      method: 'POST',
      token: null,
      body: {
        email: payload.email,
        password: payload.password,
        fullName: payload.fullName,
      },
    });
    return data;
  }

  verifyEmail(email, otp) {
    return apiRequest('/auth/verify-email', { method: 'POST', token: null, body: { email, otp } });
  }

  resendOtp(email) {
    return apiRequest('/auth/resend-otp', { method: 'POST', token: null, body: { email } });
  }

  async loginWithGoogle() {
    throw new Error('Đăng nhập Google chưa được hỗ trợ. Vui lòng đăng nhập bằng email.');
  }

  async getCurrentUser() {
    const token = localStorage.getItem('auth_token');
    if (!token) return null;
    const data = await apiRequest('/users/me');
    if (localStorage.getItem('auth_token') !== token) return null;
    const user = toClientUser(data);
    localStorage.setItem('auth_user', JSON.stringify(user));
    return user;
  }

  async logout() {
    try {
      await apiRequest('/auth/logout', { method: 'POST' });
    } finally {
      localStorage.removeItem('auth_token');
      localStorage.removeItem('auth_user');
    }
  }

  async updateProfile(updates) {
    const data = await apiRequest('/users/me', {
      method: 'PATCH',
      body: {
        ...(updates.fullName !== undefined ? { fullName: updates.fullName } : {}),
        ...(updates.targetScore !== undefined ? { targetScore: updates.targetScore } : {}),
      },
    });
    return toClientUser(data);
  }

  async changePassword(currentPassword, newPassword) {
    await apiRequest('/users/me/password', { method: 'POST', body: { currentPassword, newPassword } });
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_user');
  }
}
