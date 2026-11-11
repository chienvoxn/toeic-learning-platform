import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import AuthTemplate from '@/components/templates/AuthTemplate';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { useMock } from '@/lib/runtime-config';
import GoogleSignIn from '@/components/organisms/GoogleSignIn';

export default function LoginPage() {
  const navigate = useNavigate();
  const { state } = useLocation();
  const { login, loginWithGoogle, sessionError } = useAuth();
  const [email, setEmail] = useState(state?.email || '');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const destination = state?.from?.startsWith('/') && !state.from.startsWith('//') ? state.from : '/dashboard';
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError('');
    try { await login({ email, password }); navigate(destination, { replace: true }); }
    catch (err) { setError(err.message || 'Đăng nhập không thành công.'); }
    finally { setBusy(false); }
  }
  async function google() {
    setBusy(true); setError('');
    try { await loginWithGoogle(); navigate(destination, { replace: true }); }
    catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }
  return <AuthTemplate title="Chào mừng trở lại" subtitle="Tiếp tục hành trình học tiếng Anh">
    {state?.message && <p role="status">{state.message}</p>}
    {sessionError && <p role="status">{sessionError}</p>}
    {error && <p className="form-error" role="alert">{error}</p>}
    {!useMock && <GoogleSignIn disabled={busy} onBusyChange={setBusy} onSuccess={() => navigate(destination, { replace: true })} />}
    <form className="email-login" onSubmit={submit}>
      <label htmlFor="login-email">Email</label><Input id="login-email" type="email" autoComplete="email" maxLength={255} required value={email} onChange={event => setEmail(event.target.value)} />
      <label htmlFor="login-password">Mật khẩu</label><Input id="login-password" type="password" autoComplete="current-password" maxLength={72} required value={password} onChange={event => setPassword(event.target.value)} />
      <Button type="submit" className="navy-button" disabled={busy}>{busy ? 'Đang đăng nhập...' : 'Đăng nhập'}</Button>
    </form>
    {useMock && <button type="button" className="text-button" disabled={busy} onClick={google}>Dùng tài khoản Google mẫu</button>}
    <p className="auth-register">Chưa có tài khoản? <Link to="/register">Đăng ký ngay</Link></p>
    {!useMock && <p className="auth-register"><Link to="/forgot-password">Quên mật khẩu?</Link></p>}
  </AuthTemplate>;
}
