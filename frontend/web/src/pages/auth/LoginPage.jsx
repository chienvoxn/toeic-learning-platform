import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import AuthTemplate from '@/components/templates/AuthTemplate';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { isRemoteAuth } from '@/features/auth/config';
import { authErrorMessage } from '@/features/auth/errors';
import GoogleSignIn from '@/components/organisms/GoogleSignIn';

export default function LoginPage() {
  const navigate = useNavigate();
  const { state } = useLocation();
  const { login, loginWithGoogle, sessionError } = useAuth();
  const [email, setEmail] = useState(state?.email || '');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const destination = typeof state?.from === 'string' && state.from.startsWith('/') && !state.from.startsWith('//') ? state.from : '/dashboard';
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError('');
    try { await login({ email, password }); navigate(destination, { replace: true }); }
    catch (err) { setError(authErrorMessage(err)); }
    finally { setBusy(false); setPassword(''); }
  }
  async function google() {
    setBusy(true); setError('');
    try { await loginWithGoogle(); navigate(destination, { replace: true }); }
    catch (err) { setError(err.message); }
    finally { setBusy(false); setPassword(''); }
  }
  return <AuthTemplate title="Chào mừng trở lại" subtitle="Tiếp tục hành trình học tiếng Anh">
    {state?.message && <p role="status">{state.message}</p>}
    {sessionError && <p role="alert">{authErrorMessage(sessionError)}</p>}
    {state?.logoutFailed && <p role="alert">Đã xóa phiên trên trình duyệt, nhưng chưa xác minh được thu hồi phiên trên máy chủ vì lỗi kết nối. Phiên máy chủ có thể còn hiệu lực đến khi hết hạn.</p>}
    {state?.sessionEnded && <p role="status">Vui lòng đăng nhập để tiếp tục.</p>}
    {!isRemoteAuth && <p role="status">Auth đang ở chế độ demo, không xác thực với backend.</p>}
    {error && <p className="form-error" role="alert">{error}</p>}
    {isRemoteAuth && <GoogleSignIn disabled={busy} onBusyChange={setBusy} onSuccess={() => navigate(destination, { replace: true })} />}
    <form className="email-login" onSubmit={submit}>
      <label htmlFor="login-email">Email</label><Input id="login-email" type="email" autoComplete="email" maxLength={255} required value={email} onChange={event => setEmail(event.target.value)} />
      <label htmlFor="login-password">Mật khẩu</label><Input id="login-password" type="password" autoComplete="current-password" maxLength={72} required value={password} onChange={event => setPassword(event.target.value)} />
      <Button type="submit" className="navy-button" disabled={busy}>{busy ? 'Đang đăng nhập...' : 'Đăng nhập'}</Button>
    </form>
    {!isRemoteAuth && <button type="button" className="text-button" disabled={busy} onClick={google}>Dùng tài khoản Google mẫu</button>}
    <p className="auth-register">Chưa có tài khoản? <Link to="/register">Đăng ký ngay</Link></p>
    {isRemoteAuth && <p className="auth-register"><Link to="/forgot-password">Quên mật khẩu?</Link></p>}
  </AuthTemplate>;
}
