import { isRemoteAuth } from '@/features/auth/config';
import { authErrorMessage } from '@/features/auth/errors';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Pencil, Save, X, Camera, ImagePlus, Lock } from 'lucide-react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { FigmaAsset } from '@/components/atoms/FigmaAsset';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { figmaAsset } from '@/lib/figma-assets';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/hooks/useAuth';
import ProfileLearningPanel from './ProfileLearningPanel';
function ProfileContent({ user, updateProfile }) {
  const mock = !isRemoteAuth;
  const [editing, setEditing] = useState(false);
  const [fullName, setFullName] = useState(user?.fullName || '');
  const [targetScore, setTargetScore] = useState(user?.targetScore ?? '');
  const [bio, setBio] = useState(user?.bio || '');
  const [avatar, setAvatar] = useState(user?.avatarUrl || '');
  const [cover, setCover] = useState(user?.coverUrl || '');
  const [privateProfile, setPrivateProfile] = useState(!!user?.privateProfile);
  const [tab, setTab] = useState('Nghe chép');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  async function save(e) {
    e.preventDefault(); setLoading(true); setError('');
    try { const updates = mock ? { bio, avatarUrl: avatar, coverUrl: cover, privateProfile } : {};
      if (fullName !== user.fullName) updates.fullName = fullName;
      const score = targetScore === '' ? null : Number(targetScore);
      if (score !== null && (!Number.isInteger(score) || score < 10 || score > 990 || score % 5 !== 0)) {
        setError('Điểm mục tiêu phải là số nguyên 10–990, bội số của 5.'); return;
      }
      if (score !== user.targetScore) updates.targetScore = score;
      if (!Object.keys(updates).length) { setEditing(false); setMessage('Không có thay đổi để lưu.'); return; }
      await updateProfile(updates); setEditing(false); setMessage('Đã lưu hồ sơ.'); }
    catch (err) { setError(authErrorMessage(err)); }
    finally { setLoading(false); }
  }
  function loadImage(event, setter) {
    const file = event.target.files?.[0];
    if (!file) return;
    if (!['image/jpeg','image/png','image/webp'].includes(file.type) || file.size > 2 * 1024 * 1024) { setError('Chọn ảnh PNG, JPEG hoặc WebP nhỏ hơn 2 MB.'); return; }
    const reader = new FileReader(); reader.onload = () => setter(reader.result); reader.readAsDataURL(file);
  }
  function cancel() { setFullName(user?.fullName || ''); setTargetScore(user?.targetScore ?? ''); setBio(user?.bio || ''); setAvatar(user?.avatarUrl || ''); setCover(user?.coverUrl || ''); setPrivateProfile(!!user?.privateProfile); setEditing(false); setError(''); }
  return <>
    <div className="profile-grid"><section className="profile-card surface">
    {message && <p className="profile-message" role="status">{message}</p>}{error && <p className="form-error" role="alert">{error}</p>}
    <button type="button" className="pill-button profile-edit-button" onClick={() => { setEditing(true); setMessage(''); }}><Pencil size={13} />CHỈNH SỬA</button>
    {!editing && cover && <img className="profile-cover" src={cover} alt="Ảnh nền hồ sơ" />}
    <div className="profile-avatar-row"><div className="profile-avatar"><img src={avatar || figmaAsset('dashboard','imgAvatar')} alt="Ảnh đại diện" />{editing && <label className="avatar-upload" title={mock ? 'Đổi ảnh đại diện' : 'Chưa hỗ trợ tải ảnh lên máy chủ'}><Camera size={15} /><input className="sr-only" type="file" accept="image/png,image/jpeg,image/webp" disabled={!mock} onChange={e => loadImage(e,setAvatar)} /></label>}</div><FigmaAsset screen="profile-edit" name="imgSvg15" alt="Parroto" className="profile-parroto" /></div>
    {editing ? <form className="profile-form" onSubmit={save}><label htmlFor="profile-cover">Ảnh nền</label><label className="cover-upload" htmlFor="profile-cover">{cover ? <img src={cover} alt="Ảnh nền đã chọn" /> : <><ImagePlus size={24} /><span>Nhấp để chọn ảnh nền</span></>}<input id="profile-cover" className="sr-only" type="file" disabled={!mock} accept="image/png,image/jpeg,image/webp" onChange={e => loadImage(e,setCover)} /></label>
      <label className="sr-only" htmlFor="profile-name">Họ và tên</label><Input id="profile-name" required maxLength={150} value={fullName} onChange={e => setFullName(e.target.value)} /><small>{fullName.length}/150</small>
      <label className="sr-only" htmlFor="profile-bio">Mô tả bản thân</label><textarea id="profile-bio" disabled={!mock} maxLength={300} placeholder="Nhập mô tả về bản thân" value={bio} onChange={e => setBio(e.target.value)} /><small>{bio.length}/300</small>
      <label>Quyền riêng tư hồ sơ</label><div className="privacy-control"><Lock size={16} /><div><strong>{privateProfile ? 'Riêng tư' : 'Công khai'}</strong><p>Khi được bật, người khác sẽ không thể xem hồ sơ của bạn</p></div><button type="button" role="switch" aria-label="Hồ sơ riêng tư" aria-checked={privateProfile} disabled={!mock} className={privateProfile ? 'on' : ''} onClick={() => setPrivateProfile(!privateProfile)}><span /></button></div>
      {!mock && <p className="profile-contract-note">Ảnh, mô tả và quyền riêng tư chưa được máy chủ hỗ trợ.</p>}
      <details className="profile-target"><summary>Mục tiêu học tập</summary><label htmlFor="target-score">Điểm TOEIC mục tiêu</label><Input id="target-score" type="number" min={10} max={990} step={5} value={targetScore} placeholder="Chưa đặt mục tiêu" onChange={e => setTargetScore(e.target.value)} /><p>Listening + Reading tổng: 10–990, bội số 5. Đây là mục tiêu học tập, không phải điểm thi được quy đổi. Để trống để bỏ mục tiêu.</p></details>
      <div className="profile-form-actions"><Button type="submit" className="navy-button" disabled={loading}><Save size={15} />{loading ? 'ĐANG LƯU...' : 'LƯU'}</Button><Button variant="outline" type="button" onClick={cancel} aria-label="Hủy chỉnh sửa"><X size={18} /></Button></div>
    </form> : <><h1>{user?.fullName || 'Học viên'} {mock && <small>Lv.1</small>}</h1>{user?.bio && <p className="profile-bio">{user.bio}</p>}<div className="profile-privacy"><Lock size={14} />Quyền riêng tư hồ sơ<strong>{user?.privateProfile ? 'Riêng tư' : 'Công khai'}</strong></div><div className="profile-streak">{mock ? `🔥 ${user?.streakDays ?? 0}` : 'Streak và XP: chưa có dữ liệu từ máy chủ.'}</div><div className="profile-stat-row"><div><span>🎯</span><p>Mục tiêu TOEIC<strong>{user?.targetScore ?? 'Chưa đặt mục tiêu'}</strong></p></div><div><span>📧</span><p>Tài khoản<strong>{user?.email || 'Chưa đăng nhập'}</strong></p></div></div><div className="profile-stat-tabs">{['Nghe chép', 'Nhại theo'].map(t => <button key={t} className={tab === t ? 'active' : ''} onClick={() => setTab(t)}>{t}</button>)}</div><div className="profile-stat-line"><span>🎓 Tổng số bài học</span><strong>0</strong></div><div className="profile-stat-line"><span>📖 Tổng số câu</span><strong>0</strong></div><div className="profile-rank"><small>Cấp độ hiện tại</small><p>🛡 Chưa có hạng</p></div></>}
  </section><div className="profile-right"><section className="profile-ranking surface"><div className="profile-ranking-tabs">{['Nghe chép', 'Nhại theo'].map(t => <button key={t} className={tab === t ? 'active' : ''} onClick={() => setTab(t)}>{t.toUpperCase()}</button>)}</div><p>Chưa có lịch sử xếp hạng.</p></section><section className="profile-lessons surface"><h2>Tiến độ bài học</h2><div className="progress-status"><span>✓ Đã hoàn thành</span><span>◷ Đang học</span></div><p className="profile-empty">Chưa có tiến độ bài học.</p><Link to="/listening?mode=shadowing" className="pill-button">Khám phá bài học</Link></section></div></div>
    {!mock && user?.id && (
      <section
        className="profile-learning-section"
        aria-label="Thống kê học từ vựng"
        style={{ width: '100%', maxWidth: 1200, marginTop: 24 }}
      >
        <ProfileLearningPanel userId={user.id} />
      </section>
    )}
  </>;
}
export default function ProfilePage() {
  const { user, loading, updateProfile } = useAuth();
  return <LearnerTemplate variant="profile">{loading ? <LoadingSpinner text="Đang tải hồ sơ..." /> : <ProfileContent key={user?.id || 'guest'} user={user} updateProfile={updateProfile} />}</LearnerTemplate>;
}
