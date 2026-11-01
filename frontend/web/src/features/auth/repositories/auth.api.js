import { apiRequest } from '@/services/apiClient';
import { toClientUser } from '../api/auth.mapper';

export class RemoteAuthRepository {
  async login({ email, password }) {
    const data = await apiRequest('/auth/login', {
      method: 'POST',
      body: { email, password },
    });
    localStorage.setItem('auth_token', data.access_token);
    const user = toClientUser(data.user);
    localStorage.setItem('auth_user', JSON.stringify(user));
    return { user, token: data.access_token };
  }

  async register(payload) {
    const data = await apiRequest('/auth/register', {
      method: 'POST',
      body: {
        email: payload.email,
        password: payload.password,
        full_name: payload.fullName,
        target_score: payload.targetScore,
      },
    });
    const user = toClientUser(data.user);
    return { user, token: data.access_token };
  }

  async loginWithGoogle(idToken) {
    const data = await apiRequest('/auth/google', {
      method: 'POST',
      body: { token: idToken },
    });
    localStorage.setItem('auth_token', data.access_token);
    const user = toClientUser(data.user);
    localStorage.setItem('auth_user', JSON.stringify(user));
    return { user, token: data.access_token };
  }

  async getCurrentUser() {
    const data = await apiRequest('/users/me');
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
        full_name: updates.fullName,
        target_score: updates.targetScore,
      },
    });
    return toClientUser(data);
  }
}
