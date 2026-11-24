import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import AuthTemplate from '@/components/templates/AuthTemplate';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { isRemoteAuth } from '@/features/auth/config';

export default function ForgotPasswordPage() {
  const { forgotPassword } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(event) {
    event.preventDefault();
    setError('');
    setBusy(true);
    try {
      const result = await forgotPassword(email.trim());
      navigate('/verify-reset-otp', {
        state: { email: email.trim(), message: result?.message },
      });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return <AuthTemplate title="Quên mật khẩu" subtitle="Nhận mã xác nhận qua email để đặt lại mật khẩu">
    <p className="text-sm mb-4">Tài khoản đăng nhập bằng Google có thể tiếp tục dùng nút Google ở trang đăng nhập.</p>
    {!isRemoteAuth ? <p role="status">Khôi phục mật khẩu chỉ có trong chế độ kết nối máy chủ thật.</p> : <>
      {error && <p className="form-error" role="alert">{error}</p>}
      <form className="email-login" onSubmit={submit}>
        <label htmlFor="recovery-email">Email tài khoản</label>
        <Input id="recovery-email" type="email" required maxLength={255} autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} />
        <Button type="submit" disabled={busy}>{busy ? 'Đang gửi mã...' : 'Gửi mã đặt lại mật khẩu'}</Button>
      </form>
    </>}
    <p className="auth-register"><Link to="/login">Quay lại đăng nhập</Link></p>
  </AuthTemplate>;
}
