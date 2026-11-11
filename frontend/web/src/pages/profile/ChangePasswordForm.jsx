import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { authErrorMessage, passwordError } from '@/features/auth/errors';
import { isRemoteAuth } from '@/features/auth/config';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';

export default function ChangePasswordForm() {
  const { changePassword } = useAuth();
  const navigate = useNavigate();
  const [currentPassword, setCurrent] = useState('');
  const [newPassword, setNew] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(event) {
    event.preventDefault(); setError('');
    const validation = passwordError(currentPassword, 1) || passwordError(newPassword);
    if (validation) { setError(validation); return; }
    if (newPassword !== confirmation) { setError('Mật khẩu xác nhận không khớp.'); return; }
    setBusy(true);
    try {
      await changePassword({ currentPassword, newPassword });
      navigate('/login', { replace: true, state: { passwordChanged: true } });
    } catch (err) { setError(authErrorMessage(err)); }
    finally { setBusy(false); setCurrent(''); setNew(''); setConfirmation(''); }
  }
  return <section className="surface auth-password-section"><h2>Đổi mật khẩu</h2>
    <p>Thành công sẽ đăng xuất mọi phiên, kể cả phiên hiện tại.</p>
    {error && <p className="form-error" role="alert">{error}</p>}
    <form className="profile-form" onSubmit={submit}>
      <label htmlFor="current-password">Mật khẩu hiện tại</label><Input id="current-password" type="password" required autoComplete="current-password" value={currentPassword} onChange={e => setCurrent(e.target.value)} />
      <label htmlFor="new-password">Mật khẩu mới</label><Input id="new-password" type="password" required minLength={8} maxLength={72} autoComplete="new-password" value={newPassword} onChange={e => setNew(e.target.value)} />
      <label htmlFor="confirm-password">Xác nhận mật khẩu mới</label><Input id="confirm-password" type="password" required autoComplete="new-password" value={confirmation} onChange={e => setConfirmation(e.target.value)} />
      <small>8–72 ký tự, không quá 72 byte UTF-8. Không tự bỏ khoảng trắng.</small>
      <Button className="navy-button" type="submit" disabled={busy || !isRemoteAuth}>{busy ? 'Đang đổi mật khẩu...' : 'Đổi mật khẩu'}</Button>
      {!isRemoteAuth && <p>Đổi mật khẩu cần kết nối Identity thật.</p>}
    </form>
  </section>;
}
