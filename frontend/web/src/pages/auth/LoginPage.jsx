import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import AuthTemplate from '@/components/templates/AuthTemplate';
import { FigmaAsset } from '@/components/atoms/FigmaAsset';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useAuth } from '@/features/auth/hooks/useAuth';

export default function LoginPage() {
  const navigate = useNavigate();
  const { login, loginWithGoogle } = useAuth();
  const [emailLogin, setEmailLogin] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function handleGoogleLogin() {
    setError('');
    setLoading(true);
    try {
      await loginWithGoogle();
      navigate('/');
    } catch (err) {
      setError(err.message || 'Đăng nhập Google thất bại.');
    } finally {
      setLoading(false);
    }
  }

  async function handleSubmit(e) {
    e.preventDefault(); setError(''); setLoading(true);
    try { await login({ email, password }); navigate('/'); }
    catch (err) { setError(err.message || 'Đăng nhập không thành công.'); }
    finally { setLoading(false); }
  }
  return <AuthTemplate title="Chào mừng trở lại" subtitle="Tiếp tục hành trình học tiếng Anh">
    {error && <p className="form-error" role="alert">{error}</p>}
    {emailLogin ? <form className="email-login" onSubmit={handleSubmit}>
      <label htmlFor="login-email">Email</label><Input id="login-email" type="email" autoComplete="email" required value={email} onChange={e => setEmail(e.target.value)} placeholder="Email" />
      <label htmlFor="login-password">Mật khẩu</label><Input id="login-password" type="password" autoComplete="current-password" required value={password} onChange={e => setPassword(e.target.value)} placeholder="Mật khẩu" />
      <Button type="submit" className="navy-button" disabled={loading}>{loading ? 'Đang đăng nhập...' : 'Đăng nhập'}</Button>
      <button type="button" className="text-button" onClick={() => { setEmailLogin(false); setError(''); }}><ArrowLeft size={14} /> Quay lại</button>
    </form> : <div className="login-options">
      <Button variant="outline" className="google-login" disabled={loading} onClick={handleGoogleLogin}><FigmaAsset screen="login" name="imgSvg1" />{loading ? 'Đang xác thực...' : 'Tiếp tục với Google'}</Button>
      <div className="auth-divider"><span />HOẶC TIẾP TỤC VỚI<span /></div>
      <Button className="navy-button" onClick={() => { setEmailLogin(true); setError(''); }}>TIẾP TỤC VỚI EMAIL</Button>
    </div>}
    {emailLogin ? <p className="auth-register">Chưa có tài khoản? <Link to="/register">Đăng ký ngay</Link></p> : <p className="auth-help">Nếu bạn gặp khó khăn khi đăng nhập bằng Google, hãy thử đăng nhập bằng email</p>}
    <p className="auth-legal">Bằng cách đăng nhập, bạn đồng ý với <span>Điều khoản dịch vụ</span> và <span>Chính sách bảo mật</span> của chúng tôi</p>
  </AuthTemplate>;
}
