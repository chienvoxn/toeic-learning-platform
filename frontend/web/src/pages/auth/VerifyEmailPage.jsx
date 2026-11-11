import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import AuthTemplate from '@/components/templates/AuthTemplate';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { authErrorMessage } from '@/features/auth/errors';
import { isRemoteAuth } from '@/features/auth/config';

export default function VerifyEmailPage() {
  const navigate = useNavigate();
  const { verifyEmail, resendOtp } = useAuth();
  const [email, setEmail] = useState(() => sessionStorage.getItem('identity.pendingEmail') || '');
  const [otp, setOtp] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  async function verify(event) {
    event.preventDefault(); setBusy(true); setError(''); setMessage('');
    try {
      await verifyEmail({ email, otp });
      sessionStorage.removeItem('identity.pendingEmail');
      navigate('/login', { replace: true, state: { verified: true, email } });
    } catch (err) { setError(authErrorMessage(err)); }
    finally { setBusy(false); setOtp(''); }
  }
  async function resend() {
    setBusy(true); setError(''); setMessage('');
    try {
      await resendOtp(email);
      sessionStorage.setItem('identity.pendingEmail', email);
      setMessage('Nếu tài khoản đang chờ xác minh, mã mới đã được gửi. Vui lòng kiểm tra hộp thư.');
    } catch (err) { setError(authErrorMessage(err)); }
    finally { setBusy(false); }
  }
  return <AuthTemplate title="Xác minh email" subtitle="Nhập mã 6 chữ số trong email. Mã có hiệu lực 5 phút.">
    {!isRemoteAuth && <p role="status">Chế độ demo không gửi OTP; cần bật remote auth để xác minh thật.</p>}
    {error && <p className="form-error" role="alert">{error}</p>}
    {message && <p className="profile-message" role="status">{message}</p>}
    <form className="email-login" onSubmit={verify}>
      <label htmlFor="verify-email">Email cần xác minh</label><Input id="verify-email" type="email" required maxLength={255}
        autoComplete="email" value={email} onChange={e => setEmail(e.target.value)} />
      <label htmlFor="verify-otp">Mã OTP</label><Input id="verify-otp" inputMode="numeric" autoComplete="one-time-code"
        pattern="[0-9]{6}" maxLength={6} required value={otp} onChange={e => setOtp(e.target.value)} />
      <Button type="submit" className="navy-button" disabled={busy || !isRemoteAuth}>{busy ? 'Đang xử lý...' : 'Xác minh email'}</Button>
      <Button type="button" variant="outline" disabled={busy || !email || !isRemoteAuth} onClick={resend}>Gửi lại OTP</Button>
    </form>
    <p className="auth-help">Chờ ít nhất 60 giây giữa các lần gửi lại. Giới hạn được kiểm tra tại máy chủ.</p>
    <p className="auth-register"><Link to="/login">Đăng nhập</Link> · <Link to="/register">Đăng ký</Link></p>
  </AuthTemplate>;
}
