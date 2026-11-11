import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import AuthTemplate from '@/components/templates/AuthTemplate';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { isRemoteAuth } from '@/features/auth/config';
import { passwordError } from '@/features/auth/errors';

export default function ForgotPasswordPage() {
  const { forgotPassword, resetPassword } = useAuth();
  const navigate = useNavigate();
  const [stage, setStage] = useState('request');
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [busy, setBusy] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  useEffect(() => {
    if (!cooldown) return;
    const timer = setTimeout(() => setCooldown(value => Math.max(0, value - 1)), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);
  async function requestCode(event) {
    event?.preventDefault(); setBusy(true); setError('');
    try { const result = await forgotPassword(email); setMessage(result.message); setStage('reset'); setCooldown(60); }
    catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }
  async function submit(event) {
    event.preventDefault(); setError('');
    if (password !== confirmation) { setError('Mật khẩu xác nhận chưa khớp.'); return; }
    const validation = passwordError(password);
    if (validation) { setError(validation); return; }
    setBusy(true);
    try {
      await resetPassword(email, otp, password);
      setOtp(''); setPassword(''); setConfirmation('');
      navigate('/login', { replace: true, state: { email, message: 'Đặt lại mật khẩu thành công. Hãy đăng nhập bằng mật khẩu mới.' } });
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }
  return <AuthTemplate title="Quên mật khẩu" subtitle="Nhận mã xác nhận qua email để đặt lại mật khẩu">
    <p className="text-sm mb-4">Tài khoản đăng nhập bằng Google có thể tiếp tục dùng nút Google ở trang đăng nhập.</p>
    {!isRemoteAuth ? <p role="status">Khôi phục mật khẩu chỉ có trong chế độ kết nối máy chủ thật.</p> : <>
      {message && <p role="status" className="text-sm mb-3">{message}</p>}
      {error && <p className="form-error" role="alert">{error}</p>}
      <form className="email-login" onSubmit={stage === 'request' ? requestCode : submit}>
        <label htmlFor="recovery-email">Email tài khoản</label><Input id="recovery-email" type="email" required maxLength={255} autoComplete="email" readOnly={stage === 'reset'} value={email} onChange={event => setEmail(event.target.value)} />
        {stage === 'reset' && <>
          <label htmlFor="recovery-otp">Mã OTP gồm 6 chữ số</label><Input id="recovery-otp" required inputMode="numeric" pattern="[0-9]{6}" maxLength={6} autoComplete="one-time-code" value={otp} onChange={event => setOtp(event.target.value)} />
          <label htmlFor="recovery-password">Mật khẩu mới</label><Input id="recovery-password" required type="password" minLength={8} maxLength={72} autoComplete="new-password" value={password} onChange={event => setPassword(event.target.value)} />
          <label htmlFor="recovery-confirm">Nhập lại mật khẩu mới</label><Input id="recovery-confirm" required type="password" minLength={8} maxLength={72} autoComplete="new-password" value={confirmation} onChange={event => setConfirmation(event.target.value)} />
        </>}
        <Button type="submit" disabled={busy}>{busy ? 'Đang xử lý...' : stage === 'request' ? 'Gửi mã đặt lại mật khẩu' : 'Đặt lại mật khẩu'}</Button>
        {stage === 'reset' && <><button type="button" className="text-button" disabled={busy || cooldown > 0} onClick={() => requestCode()}>{cooldown ? `Gửi lại sau ${cooldown}s` : 'Gửi lại mã OTP'}</button><button type="button" className="text-button" disabled={busy} onClick={() => { setStage('request'); setOtp(''); setPassword(''); setConfirmation(''); setMessage(''); setError(''); }}>Đổi email</button></>}
      </form>
    </>}
    <p className="auth-register"><Link to="/login">Quay lại đăng nhập</Link></p>
  </AuthTemplate>;
}
