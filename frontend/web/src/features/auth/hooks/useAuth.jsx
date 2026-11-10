import { createContext, useContext, useEffect, useRef, useState } from 'react';
import { authRepo } from '../repositories/auth.repo';
import { useMock } from '@/lib/runtime-config';
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [sessionError, setSessionError] = useState('');
  const generation = useRef(0);
  useEffect(() => {
    let active = true;
    const version = generation.current;
    const expired = () => { generation.current++; setUser(null); setLoading(false); setSessionError('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'); };
    window.addEventListener('auth:expired', expired);
    authRepo.getCurrentUser()
      .then(current => { if (active && generation.current === version) setUser(current); })
      .catch(error => { if (active && error.status !== 401) setSessionError(error.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; window.removeEventListener('auth:expired', expired); };
  }, []);

  async function login(credentials) {
    generation.current++;
    const result = await authRepo.login(credentials);
    setUser(result.user); setSessionError('');
    return result.user;
  }
  async function loginWithGoogle(idToken) {
    generation.current++;
    const result = await authRepo.loginWithGoogle(idToken);
    setUser(result.user); setSessionError('');
    return result.user;
  }
  async function register(payload) {
    const result = await authRepo.register(payload);
    if (useMock) setUser(result.user);
    return result;
  }
  async function logout() {
    generation.current++;
    try { await authRepo.logout(); }
    catch (error) { if (error.status !== 401) setSessionError('Đã đăng xuất trên trình duyệt. Không thể xác nhận thu hồi phiên trên máy chủ; phiên sẽ hết hạn theo cấu hình.'); }
    finally { setUser(null); }
  }
  async function updateProfile(updates) {
    const version = generation.current;
    const updated = await authRepo.updateProfile(updates);
    if (generation.current === version) setUser(updated);
    return updated;
  }
  async function changePassword(currentPassword, newPassword) {
    await authRepo.changePassword(currentPassword, newPassword);
    generation.current++;
    setUser(null);
  }
  async function resetPassword(email, otp, newPassword) {
    await authRepo.resetPassword(email, otp, newPassword);
    if (user?.email?.toLowerCase() === email.trim().toLowerCase()) {
      generation.current++; setUser(null); setSessionError('');
      localStorage.removeItem('auth_token'); localStorage.removeItem('auth_user');
    }
  }
  return <AuthContext.Provider value={{ user, loading, sessionError, login, loginWithGoogle, register, logout, updateProfile, changePassword, resetPassword, forgotPassword: email => authRepo.forgotPassword(email) }}>{children}</AuthContext.Provider>;
}
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within an AuthProvider');
  return context;
}
