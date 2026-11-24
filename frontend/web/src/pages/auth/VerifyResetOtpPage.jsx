import { useEffect, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import AuthTemplate from '@/components/templates/AuthTemplate';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/hooks/useAuth';

export default function VerifyResetOtpPage() {
  const { state } = useLocation();
  const navigate = useNavigate();
  const { verifyResetOtp, forgotPassword } = useAuth();
  const email = state?.email;
  const [otp, setOtp] = useState('');
  const [busy, setBusy] = useState(false);
  const [cooldown, setCooldown] = useState(60);
  const [message, setMessage] = useState(state?.message || 'Nếu email có tài khoản dùng mật khẩu, mã OTP sẽ được gửi. Vui lòng kiểm tra hộp thư.');
  const [error, setError] = useState('');

  useEffect(() => {
    if (cooldown === 0) return;
    const timer = setTimeout(() => setCooldown(value => Math.max(0, value - 1)), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);

  if (!email) return <Navigate to="/forgot-password" replace />;

  async function submit(event) {
    event.preventDefault();
    setError('');
    setBusy(true);
    try {
      const { resetToken } = await verifyResetOtp(email, otp);
      setOtp('');
      navigate('/reset-password', { replace: true, state: { email, resetToken } });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function resend() {
    setError('');
    setBusy(true);
    try {
      const result = await forgotPassword(email);
      setMessage(result?.message || 'Vui lòng kiểm tra hộp thư để lấy mã OTP mới.');
      setOtp('');
      setCooldown(60);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return <AuthTemplate title="Xác minh OTP" subtitle="Nhập mã gồm 6 chữ số đã gửi tới email của bạn">
    <p className="text-sm mb-4">Email: <strong>{email}</strong></p>
    {message && <p role="status" className="text-sm mb-3">{message}</p>}
    {error && <p className="form-error" role="alert">{error}</p>}
    <form className="email-login" onSubmit={submit}>
      <label htmlFor="recovery-otp">Mã OTP gồm 6 chữ số</label>
      <Input id="recovery-otp" required inputMode="numeric" pattern="[0-9]{6}" maxLength={6} autoComplete="one-time-code" value={otp} onChange={event => setOtp(event.target.value)} />
      <Button type="submit" disabled={busy}>{busy ? 'Đang xác minh...' : 'Xác minh OTP'}</Button>
      <button type="button" className="text-button" disabled={busy || cooldown > 0} onClick={resend}>{cooldown > 0 ? `Gửi lại sau ${cooldown}s` : 'Gửi lại mã OTP'}</button>
    </form>
    <p className="auth-register"><Link to="/forgot-password">Đổi email</Link></p>
    <p className="auth-register"><Link to="/login">Quay lại đăng nhập</Link></p>
  </AuthTemplate>;
}
