import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import AuthTemplate from '@/components/templates/AuthTemplate';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { passwordError } from '@/features/auth/errors';

export default function ResetPasswordPage() {
  const { state } = useLocation();
  const navigate = useNavigate();
  const { resetPassword } = useAuth();
  const email = state?.email;
  const resetToken = state?.resetToken;
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  if (!email || !resetToken) return <Navigate to="/forgot-password" replace />;

  async function submit(event) {
    event.preventDefault();
    setError('');
    if (password !== confirmation) { setError('Mật khẩu xác nhận chưa khớp.'); return; }
    const validation = passwordError(password);
    if (validation) { setError(validation); return; }
    setBusy(true);
    try {
      await resetPassword(email, resetToken, password);
      setPassword('');
      setConfirmation('');
      navigate('/login', { replace: true, state: { email, message: 'Đặt lại mật khẩu thành công. Hãy đăng nhập bằng mật khẩu mới.' } });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return <AuthTemplate title="Đặt lại mật khẩu" subtitle="OTP đã xác minh. Hãy tạo mật khẩu mới">
    <p className="text-sm mb-4">Email: <strong>{email}</strong></p>
    {error && <p className="form-error" role="alert">{error}</p>}
    <form className="email-login" onSubmit={submit}>
      <label htmlFor="recovery-password">Mật khẩu mới</label>
      <Input id="recovery-password" required type="password" minLength={8} maxLength={72} autoComplete="new-password" value={password} onChange={event => setPassword(event.target.value)} />
      <label htmlFor="recovery-confirm">Nhập lại mật khẩu mới</label>
      <Input id="recovery-confirm" required type="password" minLength={8} maxLength={72} autoComplete="new-password" value={confirmation} onChange={event => setConfirmation(event.target.value)} />
      <Button type="submit" disabled={busy}>{busy ? 'Đang đặt lại...' : 'Đặt lại mật khẩu'}</Button>
    </form>
    <p className="auth-register"><Link to="/forgot-password">Yêu cầu mã OTP mới</Link></p>
  </AuthTemplate>;
}
