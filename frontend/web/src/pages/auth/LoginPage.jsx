import { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import AuthTemplate from '@/components/templates/AuthTemplate';
import { FigmaAsset } from '@/components/atoms/FigmaAsset';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { authErrorMessage } from '@/features/auth/errors';
import { isRemoteAuth } from '@/features/auth/config';

export default function LoginPage() {
  const navigate = useNavigate();
  const { state } = useLocation();
  const { login } = useAuth();
  const [email, setEmail] = useState(state?.email || '');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  async function submit(event) {
    event.preventDefault(); setError(''); setLoading(true);
    try {
      await login({ email, password });
      const destination = state?.from;
      navigate(typeof destination === 'string' && destination.startsWith('/') && !destination.startsWith('//') ? destination : '/profile', { replace: true });
    } catch (err) { setError(authErrorMessage(err)); }
    finally { setLoading(false); setPassword(''); }
  }
  return <AuthTemplate title="Chào mừng trở lại" subtitle="Tiếp tục hành trình học tiếng Anh">
    {state?.verified && <p role="status">Email đã xác minh. Vui lòng đăng nhập.</p>}
    {state?.passwordChanged && <p role="status">Đã đổi mật khẩu và thu hồi mọi phiên. Vui lòng đăng nhập lại.</p>}
    {state?.logoutFailed && <p role="alert">Đã xóa phiên trên trình duyệt, nhưng chưa xác minh được thu hồi phiên trên máy chủ vì lỗi kết nối. Phiên máy chủ có thể còn hiệu lực đến khi hết hạn.</p>}
    {state?.sessionEnded && <p role="status">Vui lòng đăng nhập để tiếp tục.</p>}
    {!isRemoteAuth && <p role="status">Auth đang ở chế độ demo, không xác thực với backend.</p>}
    {error && <p className="form-error" role="alert">{error}</p>}
    <Button variant="outline" className="google-login" disabled><FigmaAsset screen="login" name="imgSvg1" />Google login — chưa hỗ trợ</Button>
    <div className="auth-divider"><span />ĐĂNG NHẬP BẰNG EMAIL<span /></div>
    <form className="email-login" onSubmit={submit}>
      <label htmlFor="login-email">Email</label><Input id="login-email" type="email" autoComplete="email" maxLength={255} required value={email} onChange={e => setEmail(e.target.value)} />
      <label htmlFor="login-password">Mật khẩu</label><Input id="login-password" type="password" autoComplete="current-password" required maxLength={72} value={password} onChange={e => setPassword(e.target.value)} />
      <Button type="submit" className="navy-button" disabled={loading}>{loading ? 'Đang đăng nhập...' : 'Đăng nhập'}</Button>
    </form>
    <p className="auth-register">Chưa có tài khoản? <Link to="/register">Đăng ký ngay</Link></p>
    <p className="auth-register"><Link to="/verify-email">Xác minh email / Gửi lại OTP</Link></p>
  </AuthTemplate>;
}
