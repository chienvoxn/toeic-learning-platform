import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, Eye, EyeOff } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { useMock } from '@/lib/runtime-config';

export default function RegisterPage() {
  const navigate = useNavigate();
  const { register } = useAuth();
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [visible, setVisible] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  async function handleSubmit(e) {
    e.preventDefault(); setLoading(true); setError('');
    if (password !== confirmation) { setError('Mật khẩu xác nhận chưa khớp.'); setLoading(false); return; }
    if (new TextEncoder().encode(password).length > 72) { setError('Mật khẩu không được vượt quá 72 byte UTF-8.'); setLoading(false); return; }
    try { await register({ fullName, email, password, targetScore: 750 }); navigate(useMock ? '/' : '/login', { replace: true, state: { email, message: 'Tạo tài khoản thành công. Bạn có thể đăng nhập ngay, không cần OTP.' } }); }
    catch (err) { setError(err.message || 'Không thể tạo tài khoản.'); }
    finally { setLoading(false); }
  }
  return <main className="register-page"><header><Link to="/login" aria-label="Quay lại"><ArrowLeft size={24} /></Link><Link className="register-login" to="/login">ĐĂNG NHẬP</Link></header>
    <form className="register-form" onSubmit={handleSubmit}><h1>Đăng ký tài khoản</h1>
      <p className="text-sm text-slate-500 mb-4">Tạo tài khoản để bắt đầu học và lưu tiến độ của bạn.</p>
      {error && <p className="form-error" role="alert">{error}</p>}
      <label className="sr-only" htmlFor="register-name">Họ và tên</label><Input id="register-name" required maxLength={150} autoComplete="name" placeholder="Họ và tên" value={fullName} onChange={e => setFullName(e.target.value)} />
      <label className="sr-only" htmlFor="register-email">Email</label><Input id="register-email" type="email" autoComplete="email" maxLength={255} required placeholder="Email" value={email} onChange={e => setEmail(e.target.value)} />
      <div className="password-field"><label className="sr-only" htmlFor="register-password">Mật khẩu</label><Input id="register-password" type={visible ? 'text' : 'password'} autoComplete="new-password" minLength={8} maxLength={72} required placeholder="Mật khẩu" value={password} onChange={e => setPassword(e.target.value)} /><button type="button" className="icon-button" aria-label={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'} onClick={() => setVisible(!visible)}>{visible ? <EyeOff size={22} /> : <Eye size={22} />}</button></div>
      <label className="sr-only" htmlFor="register-confirm">Nhập lại mật khẩu</label><Input id="register-confirm" type={visible ? 'text' : 'password'} autoComplete="new-password" minLength={8} maxLength={72} required placeholder="Nhập lại mật khẩu" value={confirmation} onChange={event => setConfirmation(event.target.value)} />
      <Button type="submit" disabled={loading}>{loading ? 'ĐANG TẠO...' : 'TẠO TÀI KHOẢN'}</Button>
      <p>Bằng cách đăng ký, bạn đồng ý với <strong>Điều khoản</strong> và <strong>Chính sách bảo mật</strong> của chúng tôi.</p>
    </form></main>;
}
