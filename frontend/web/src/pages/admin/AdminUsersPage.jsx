import { useEffect, useState } from 'react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { adminUsersApi } from '@/features/admin/admin-users.api';

export default function AdminUsersPage() {
  const [draft, setDraft] = useState({ q: '', role: '', status: '' });
  const [query, setQuery] = useState({ q: '', role: '', status: '', page: 0 });
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(null);
  const [notice, setNotice] = useState('');
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    // Clear old results before the request; never render stale data after permission errors.
    Promise.resolve().then(() => {
      if (controller.signal.aborted) return;
      setLoading(true); setData(null); setError('');
      return adminUsersApi.list(query, controller.signal).then(result => {
        if (!controller.signal.aborted) setData(result);
      }).catch(err => {
        if (!controller.signal.aborted) setError(err.status === 403 ? 'Bạn không có quyền quản lý người dùng.' : err.message);
      }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    });
    return () => controller.abort();
  }, [query, revision]);
  async function change(user) {
    const next = user.status === 'ACTIVE' ? 'LOCKED' : 'ACTIVE';
    if (!window.confirm(`${next === 'LOCKED' ? 'Khóa' : 'Mở khóa'} tài khoản ${user.email}?`)) return;
    setBusy(user.userId); setError(''); setNotice('');
    try {
      await adminUsersApi.changeStatus(user.userId, next);
      setNotice(next === 'LOCKED' ? 'Đã khóa tài khoản.' : 'Đã mở khóa tài khoản.'); setRevision(value => value + 1);
    } catch (err) { setError(err.status === 403 ? 'Bạn không có quyền quản lý người dùng.' : err.message); }
    finally { setBusy(null); }
  }
  const field = (name, value) => setDraft(previous => ({ ...previous, [name]: value }));
  return <LearnerTemplate><div className="max-w-6xl mx-auto space-y-5">
    <div className="page-heading"><h1>Quản lý người dùng</h1><p>Tìm Learner/Teacher và khóa hoặc mở khóa tài khoản. Không quản lý role/permission ở đây.</p></div>
    <form className="surface p-5 flex flex-wrap gap-4 items-end" onSubmit={event => { event.preventDefault(); setNotice(''); setQuery({ ...draft, page: 0 }); }}>
      <label className="grid gap-2 flex-1 min-w-48" htmlFor="admin-search">Tên hoặc email<input id="admin-search" className="border rounded-lg p-2" maxLength={200} value={draft.q} onChange={event => field('q', event.target.value)} /></label>
      <label className="grid gap-2" htmlFor="admin-role">Vai trò<select id="admin-role" className="border rounded-lg p-2" value={draft.role} onChange={event => field('role', event.target.value)}><option value="">Learner và Teacher</option><option value="LEARNER">Learner</option><option value="TEACHER">Teacher</option></select></label>
      <label className="grid gap-2" htmlFor="admin-status">Trạng thái<select id="admin-status" className="border rounded-lg p-2" value={draft.status} onChange={event => field('status', event.target.value)}><option value="">Tất cả</option>{['ACTIVE','LOCKED','PENDING','DISABLED'].map(status => <option key={status}>{status}</option>)}</select></label>
      <button className="pill-button navy" type="submit" disabled={loading || !!busy}>Tìm kiếm</button>
    </form>
    <p className="text-sm text-slate-500">Khóa chặn đăng nhập và request tiếp theo của phiên đang dùng. Theo chính sách hiện hành, mở khóa cho phép phiên còn hạn, chưa thu hồi hoạt động lại. Chỉ đổi giữa ACTIVE và LOCKED; không thay trạng thái PENDING/DISABLED.</p>
    {error && <p role="alert" className="form-error">{error}</p>}{notice && <p role="status">{notice}</p>}
    {loading && <LoadingSpinner text="Đang tải danh sách người dùng..." />}
    {data && <section className="surface p-5 space-y-4"><p>Tìm thấy {data.totalElements} tài khoản</p><div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr>{['Họ tên','Email','Vai trò','Trạng thái','Thao tác'].map(title => <th key={title} className="p-3">{title}</th>)}</tr></thead><tbody>{data.items.map(user => <tr key={user.userId} className="border-t"><td className="p-3">{user.fullName}</td><td className="p-3">{user.email}</td><td className="p-3">{user.roles.join(', ')}</td><td className="p-3">{user.status}</td><td className="p-3">{['ACTIVE','LOCKED'].includes(user.status) ? <button type="button" className="pill-button" disabled={!!busy} onClick={() => change(user)}>{busy === user.userId ? 'Đang lưu...' : user.status === 'ACTIVE' ? 'Khóa' : 'Mở khóa'}</button> : 'Không đổi'}</td></tr>)}</tbody></table></div>
      {!data.items.length && <p>Không có tài khoản phù hợp.</p>}
      <div className="flex items-center gap-4"><button className="pill-button" disabled={loading || !!busy || query.page === 0} onClick={() => setQuery(previous => ({ ...previous, page: previous.page - 1 }))}>Trang trước</button><span>Trang {data.page + 1}</span><button className="pill-button" disabled={loading || !!busy || (data.page + 1) * data.size >= data.totalElements} onClick={() => setQuery(previous => ({ ...previous, page: previous.page + 1 }))}>Trang sau</button></div>
    </section>}
  </div></LearnerTemplate>;
}
