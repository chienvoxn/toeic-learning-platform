import { toDemoUser as toClientUser } from '../api/auth.mapper';

const delay = (ms = 350) => new Promise(res => setTimeout(res, ms));

const MOCK_LEARNER_DTO = {
  id: 'usr_learner_01',
  email: 'learner@example.com',
  full_name: 'Nguyễn Văn Học Viên',
  target_score: 850,
  streak_days: 14,
  roles: ['LEARNER'],
};

const MOCK_TEACHER_DTO = {
  id: 'usr_teacher_01',
  email: 'teacher@example.com',
  full_name: 'Trần Thị Giảng Viên',
  target_score: 990,
  streak_days: 45,
  roles: ['TEACHER'],
};

export class FakeAuthRepository {
  async login({ email, password }) {
    await delay(400);
    if (!email || !password) {
      throw new Error('Vui lòng nhập đầy đủ thông tin đăng nhập.');
    }
    const isTeacher = email.includes('teacher');
    const userDto = isTeacher ? MOCK_TEACHER_DTO : { ...MOCK_LEARNER_DTO, email };
    const user = toClientUser(userDto);
    const token = 'fake_jwt_token_' + Date.now();

    localStorage.setItem('auth_token', token);
    localStorage.setItem('auth_user', JSON.stringify(user));
    return { user, token };
  }

  async register({ email, fullName, _password, targetScore }) {
    await delay(500);
    const userDto = {
      id: 'usr_' + Date.now(),
      email,
      full_name: fullName,
      target_score: Number(targetScore) || 750,
      streak_days: 1,
      roles: ['LEARNER'],
    };
    const user = toClientUser(userDto);
    const token = 'fake_jwt_token_' + Date.now();
    localStorage.setItem('auth_token', token);
    localStorage.setItem('auth_user', JSON.stringify(user));
    return { user, token };
  }

  async changePassword() { throw new Error('Đổi mật khẩu cần VITE_AUTH_MODE=remote.'); }
  async forgotPassword() { throw new Error('Khôi phục mật khẩu cần VITE_AUTH_MODE=remote.'); }
  async resetPassword() { throw new Error('Khôi phục mật khẩu cần VITE_AUTH_MODE=remote.'); }

  async loginWithGoogle() {
    await delay(350);
    const userDto = {
      ...MOCK_LEARNER_DTO,
      email: 'google.learner@gmail.com',
      full_name: 'Nguyễn Văn Học Viên (Google)',
    };
    const user = toClientUser(userDto);
    const token = 'fake_google_jwt_' + Date.now();
    localStorage.setItem('auth_token', token);
    localStorage.setItem('auth_user', JSON.stringify(user));
    return { user, token };
  }

  async getCurrentUser() {
    await delay(200);
    const token = localStorage.getItem('auth_token');
    const stored = localStorage.getItem('auth_user');
    // Chỉ khôi phục phiên mock sau khi người dùng thực sự đăng nhập.
    if (!token || !stored) return null;
    try {
      return JSON.parse(stored);
    } catch {
      return null;
    }
  }

  async logout() {
    await delay(150);
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_user');
  }

  async updateProfile(updates) {
    await delay(300);
    const current = await this.getCurrentUser();
    const updated = { ...current, ...updates };
    localStorage.setItem('auth_user', JSON.stringify(updated));
    return updated;
  }
}
