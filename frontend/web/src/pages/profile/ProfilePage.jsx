import { useState } from 'react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { Pencil, Save, X } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { authErrorMessage } from '@/features/auth/errors';
import ChangePasswordForm from './ChangePasswordForm';

function ProfileContent({ user, updateProfile }) {
  const [editing, setEditing] = useState(false);
  const [fullName, setFullName] = useState(user.fullName);
  const [targetScore, setTargetScore] = useState(user.targetScore ?? '');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  function edit() {
    setFullName(user.fullName); setTargetScore(user.targetScore ?? '');
    setMessage(''); setError(''); setEditing(true);
  }
  async function save(event) {
    event.preventDefault(); setError(''); setMessage('');
    const updates = {};
    if (fullName !== user.fullName) updates.fullName = fullName;
    const score = targetScore === '' ? null : Number(targetScore);
    if (score !== null && (!Number.isInteger(score) || score < 10 || score > 990 || score % 5 !== 0)) {
      setError('Điểm mục tiêu phải là số nguyên 10–990, bội số của 5.'); return;
    }
    if (score !== user.targetScore) updates.targetScore = score;
    if (!Object.keys(updates).length) { setEditing(false); return; }
    setBusy(true);
    try { await updateProfile(updates); setEditing(false); setMessage('Đã lưu hồ sơ.'); }
    catch (err) { setError(authErrorMessage(err)); }
    finally { setBusy(false); }
  }
  return <div className="profile-grid">
    <section className="profile-card surface">
      {message && <p className="profile-message" role="status">{message}</p>}
      {error && <p className="form-error" role="alert">{error}</p>}
      {!editing && <button type="button" className="pill-button profile-edit-button" onClick={edit}><Pencil size={13} />CHỈNH SỬA</button>}
      <div className="profile-avatar-row"><div className="profile-avatar">
        <span aria-label="Chữ đầu tên" className="profile-initial">{user.fullName?.charAt(0)?.toUpperCase()}</span>
      </div></div>
      {editing ? <form className="profile-form" onSubmit={save}>
        <label htmlFor="profile-name">Họ và tên</label><Input id="profile-name" required maxLength={150} autoComplete="name"
          value={fullName} onChange={e => setFullName(e.target.value)} /><small>{fullName.length}/150</small>
        <label htmlFor="target-score">Điểm TOEIC mục tiêu</label>
        <Input id="target-score" type="number" min={10} max={990} step={5} value={targetScore}
          placeholder="Chưa đặt mục tiêu" onChange={e => setTargetScore(e.target.value)} />
        <small>Listening + Reading tổng, 10–990, bội số của 5. Để trống để xóa mục tiêu.</small>
        <p className="profile-contract-note">Chưa hỗ trợ đổi email, tải avatar, mô tả hoặc quyền riêng tư hồ sơ.</p>
        <div className="profile-form-actions"><Button type="submit" className="navy-button" disabled={busy}><Save size={15} />{busy ? 'ĐANG LƯU...' : 'LƯU'}</Button>
          <Button variant="outline" type="button" disabled={busy} onClick={() => { setEditing(false); setError(''); }} aria-label="Hủy chỉnh sửa"><X size={18} /></Button></div>
      </form> : <>
        <h1>{user.fullName}</h1>
        <div className="profile-stat-row"><div><span>🎯</span><p>Mục tiêu TOEIC<strong>{user.targetScore ?? 'Chưa đặt mục tiêu'}</strong></p></div>
          <div><span>📧</span><p>Tài khoản<strong>{user.email}</strong></p></div></div>
        <p className="profile-contract-note">Mã tài khoản: {user.userId}</p>
        <p className="profile-contract-note">Vai trò: {user.roles.join(', ')}</p>
        <p className="profile-empty">Streak, XP, xếp hạng và tiến độ: chưa có dữ liệu từ máy chủ.</p>
      </>}
    </section>
    <div className="profile-right"><ChangePasswordForm /></div>
  </div>;
}

export default function ProfilePage() {
  const { user, updateProfile } = useAuth();
  return <LearnerTemplate variant="profile"><ProfileContent key={user.userId} user={user} updateProfile={updateProfile} /></LearnerTemplate>;
}
