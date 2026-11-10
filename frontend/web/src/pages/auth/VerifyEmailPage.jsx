import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import AuthTemplate from '@/components/templates/AuthTemplate';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/hooks/useAuth';

export default function VerifyEmailPage() {
  const { state } = useLocation();
  const navigate = useNavigate();
  const { verifyEmail, resendOtp } = useAuth();
  const [email, setEmail] = useState(state?.email || '');
  const [otp, setOtp] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('Nhập mã OTP được gửi đến email của bạn.');
  const [cooldown, setCooldown] = useState(state?.email ? 60 : 0);
  useEffect(() => {
    if (!cooldown) return;
    const timer = setTimeout(() => setCooldown(value => Math.max(0, value - 1)), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      await verifyEmail(email, otp);
      navigate('/login', { replace: true, state: { message: 'Xác minh thành công. Bạn có thể đăng nhập bằng email.' } });
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }
  async function resend() {
    setBusy(true); setError('');
    try { const result = await resendOtp(email); setMessage(result.message); setCooldown(60); }
    catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }
  return <AuthTemplate title="Xác minh email" subtitle="Hoàn tất đăng ký để bắt đầu học">
    <p role="status">{message}</p>
    {error && <p className="form-error" role="alert">{error}</p>}
    <form className="email-login" onSubmit={submit}>
      <label htmlFor="verify-email">Email</label><Input id="verify-email" type="email" required maxLength={255} autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} />
      <label htmlFor="verify-otp">Mã OTP gồm 6 chữ số</label><Input id="verify-otp" required inputMode="numeric" pattern="[0-9]{6}" maxLength={6} autoComplete="one-time-code" value={otp} onChange={event => setOtp(event.target.value)} />
      <Button type="submit" disabled={busy}>{busy ? 'Đang xử lý...' : 'Xác minh email'}</Button>
      <button type="button" className="text-button" disabled={busy || cooldown > 0 || !email} onClick={resend}>{cooldown ? `Gửi lại sau ${cooldown}s` : 'Gửi lại mã OTP'}</button>
    </form>
    <Link className="text-button" to="/login">Về trang đăng nhập</Link>
  </AuthTemplate>;
}
