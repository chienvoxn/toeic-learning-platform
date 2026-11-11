import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { authErrorMessage, passwordError } from '@/features/auth/errors';

export default function PasswordChangeForm() {
  const { changePassword } = useAuth();
  const navigate = useNavigate();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit(event) {
    event.preventDefault(); setError('');
    if (next !== confirm) { setError('Mật khẩu xác nhận chưa khớp.'); return; }
    const validation = passwordError(current, 1) || passwordError(next);
    if (validation) { setError(validation); return; }
    setBusy(true);
    try { await changePassword(current, next); navigate('/login', { replace: true, state: { message: 'Đổi mật khẩu thành công. Vui lòng đăng nhập lại.' } }); }
    catch (err) { setError(authErrorMessage(err)); }
    finally { setBusy(false); setCurrent(''); setNext(''); setConfirm(''); }
  }
  return <section className="surface p-5"><h2 className="font-bold mb-4">Đổi mật khẩu</h2>{error && <p role="alert" className="form-error">{error}</p>}<form className="space-y-3" onSubmit={submit}>
    <label className="block text-sm" htmlFor="current-password">Mật khẩu hiện tại<input id="current-password" className="w-full border p-2 rounded-lg mt-1" required type="password" autoComplete="current-password" maxLength={72} value={current} onChange={event => setCurrent(event.target.value)} /></label>
    <label className="block text-sm" htmlFor="new-password">Mật khẩu mới<input id="new-password" className="w-full border p-2 rounded-lg mt-1" required type="password" autoComplete="new-password" minLength={8} maxLength={72} value={next} onChange={event => setNext(event.target.value)} /></label>
    <label className="block text-sm" htmlFor="confirm-password">Nhập lại mật khẩu mới<input id="confirm-password" className="w-full border p-2 rounded-lg mt-1" required type="password" autoComplete="new-password" minLength={8} maxLength={72} value={confirm} onChange={event => setConfirm(event.target.value)} /></label>
    <p className="text-xs text-slate-500">Đổi mật khẩu sẽ đăng xuất tất cả phiên của tài khoản.</p><button className="pill-button navy" type="submit" disabled={busy}>{busy ? 'Đang lưu...' : 'Đổi mật khẩu'}</button>
  </form></section>;
}
