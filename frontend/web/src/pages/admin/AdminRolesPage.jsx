import { useEffect, useState } from 'react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { rbacApi } from '@/features/admin/rbac.api';
import { adminUsersApi } from '@/features/admin/admin-users.api';

const blank = { name: '', displayName: '', description: '' };
const toggle = (ids, id) => ids.includes(id) ? ids.filter(value => value !== id) : [...ids, id];
async function allRoles(signal) {
  let page = 0; const result = [];
  while (true) {
    const data = await rbacApi.roles({ page, size: 100 }, signal); result.push(...data.items);
    if (result.length >= data.totalElements) return result;
    page++;
  }
}

export default function AdminRolesPage() {
  const { user, restore } = useAuth();
  const allowed = user?.adminCapabilities?.rbacManagement === true;
  const [tab, setTab] = useState('roles');
  const [draft, setDraft] = useState('');
  const [query, setQuery] = useState({ q: '', page: 0 });
  const [data, setData] = useState(null);
  const [catalog, setCatalog] = useState([]);
  const [permissions, setPermissions] = useState([]);
  const [selected, setSelected] = useState(null);
  const [form, setForm] = useState(blank);
  const [chosen, setChosen] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    Promise.resolve().then(async () => {
      if (controller.signal.aborted) return;
      setData(null); setSelected(null); setLoading(true); setError('');
      if (!allowed) { setLoading(false); return; }
      try {
        const [page, roles, rights] = await Promise.all([
          tab === 'roles' ? rbacApi.roles(query, controller.signal) : rbacApi.users(query, controller.signal),
          allRoles(controller.signal), rbacApi.permissions(controller.signal),
        ]);
        if (!controller.signal.aborted) { setData(page); setCatalog(roles); setPermissions(rights); }
      } catch (err) {
        if (!controller.signal.aborted) {
          setError(err.message);
          if (err.status === 403) await restore();
        }
      } finally { if (!controller.signal.aborted) setLoading(false); }
    });
    return () => controller.abort();
  }, [allowed, tab, query, revision, restore]);

  async function select(item) {
    setBusy(true); setError(''); setNotice(''); setSelected(null);
    try {
      const detail = tab === 'roles' ? await rbacApi.role(item.roleId) : await rbacApi.user(item.userId);
      setSelected(detail);
      setForm({ name: detail.name ?? '', displayName: detail.displayName ?? '', description: detail.description ?? '' });
      setChosen(tab === 'roles' ? detail.permissionIds : detail.roles.map(role => role.roleId));
    } catch (err) { setError(err.message); if (err.status === 403) { setData(null); await restore(); } }
    finally { setBusy(false); }
  }
  async function mutate(action, message) {
    setBusy(true); setError(''); setNotice('');
    try {
      await action(); setSelected(null); setNotice(message);
      await restore({ background: true }); setRevision(value => value + 1);
    } catch (err) {
      setError(err.message);
      if (err.status === 403) { setSelected(null); setData(null); await restore(); }
    } finally { setBusy(false); }
  }
  function saveRole(event) {
    event.preventDefault();
    const payload = { ...form, displayName: form.displayName.trim() || null, description: form.description.trim() || null };
    if (selected.roleId && !selected.canRename) delete payload.name;
    void mutate(() => selected.roleId ? rbacApi.edit(selected.roleId, payload) : rbacApi.create(payload), 'Đã lưu vai trò.');
  }
  function savePermissions() {
    if (!window.confirm('Thay đổi quyền có hiệu lực với phiên đang dùng ở request tiếp theo. Tiếp tục?')) return;
    void mutate(() => rbacApi.setPermissions(selected.roleId, chosen), 'Đã lưu quyền của vai trò.');
  }
  function saveUserRoles() {
    const previous = selected.roles.map(role => role.roleId);
    const changesAdmin = catalog.some(role => role.administrativeRole && chosen.includes(role.roleId) !== previous.includes(role.roleId));
    if (!window.confirm(changesAdmin ? 'Xác nhận cấp/thu hồi ADMIN cho tài khoản này? Quyền có hiệu lực ngay ở request tiếp theo.' : 'Xác nhận thay đổi vai trò của tài khoản?')) return;
    void mutate(() => rbacApi.setRoles(selected.userId, chosen), 'Đã lưu vai trò của tài khoản.');
  }
  function removeRole() {
    if (!window.confirm(`Xóa vai trò ${selected.displayName} (${selected.name})? Chỉ xóa khi không còn tài khoản sử dụng.`)) return;
    void mutate(() => rbacApi.remove(selected.roleId), 'Đã xóa vai trò.');
  }
  function changeStatus() {
    const status = selected.status === 'ACTIVE' ? 'LOCKED' : 'ACTIVE';
    if (!window.confirm(`${status === 'LOCKED' ? 'Khóa' : 'Mở khóa'} tài khoản? Hệ thống luôn phải giữ một Admin ACTIVE đủ quyền quản trị.`)) return;
    void mutate(() => adminUsersApi.changeStatus(selected.userId, status), 'Đã cập nhật trạng thái tài khoản.');
  }
  const field = (name, value) => setForm(previous => ({ ...previous, [name]: value }));
  return <LearnerTemplate><div className="max-w-6xl mx-auto space-y-5">
    <div className="page-heading"><h1>Vai trò và quyền</h1><p>Quản lý RBAC bằng API thật. Quyền có hiệu lực ở request tiếp theo; không tạo permission tùy ý.</p></div>
    {!allowed ? <p role="alert" className="form-error">Bạn không có quyền quản trị RBAC.</p> : <>
      <div className="flex gap-3"><button className="pill-button" disabled={busy} aria-pressed={tab === 'roles'} onClick={() => { setTab('roles'); setQuery({ q: '', page: 0 }); setDraft(''); }}>Vai trò</button><button className="pill-button" disabled={busy} aria-pressed={tab === 'users'} onClick={() => { setTab('users'); setQuery({ q: '', page: 0 }); setDraft(''); }}>Gán vai trò tài khoản</button></div>
      <form className="surface p-5 flex gap-3 items-end" onSubmit={event => { event.preventDefault(); setQuery({ q: draft, page: 0 }); }}>
        <label className="grid gap-2 flex-1" htmlFor="rbac-search">{tab === 'roles' ? 'Tìm mã hoặc tên vai trò' : 'Tìm tên hoặc email tài khoản'}<input id="rbac-search" className="border rounded-lg p-2" value={draft} maxLength={200} onChange={event => setDraft(event.target.value)} /></label>
        <button className="pill-button navy" disabled={busy || loading}>Tìm kiếm</button>
        {tab === 'roles' && <button className="pill-button" type="button" disabled={busy || loading} onClick={() => { setSelected({}); setForm(blank); setChosen([]); setError(''); }}>Tạo vai trò</button>}
      </form>
      {error && <p role="alert" className="form-error">{error}</p>}{notice && <p role="status">{notice}</p>}
      {loading && <LoadingSpinner text="Đang tải RBAC..." />}
      {data && <section className="surface p-5 space-y-3"><p>Tìm thấy {data.totalElements} {tab === 'roles' ? 'vai trò' : 'tài khoản'}</p><ul className="divide-y">{data.items.map(item => <li className="py-3 flex justify-between gap-4" key={item.roleId ?? item.userId}>
        <span>{tab === 'roles' ? `${item.displayName} (${item.name}) · ${item.userCount} tài khoản${item.systemRole ? ' · Hệ thống' : ''}` : `${item.fullName} · ${item.email} · ${item.status}`}</span>
        <button className="pill-button" disabled={busy} onClick={() => select(item)}>Quản lý</button>
      </li>)}</ul>{!data.items.length && <p>Không có kết quả.</p>}
        <div className="flex gap-4 items-center"><button className="pill-button" disabled={busy || !data.page} onClick={() => setQuery(previous => ({ ...previous, page: previous.page - 1 }))}>Trang trước</button><span>Trang {data.page + 1}</span><button className="pill-button" disabled={busy || (data.page + 1) * data.size >= data.totalElements} onClick={() => setQuery(previous => ({ ...previous, page: previous.page + 1 }))}>Trang sau</button></div>
      </section>}
      {selected && <section className="surface p-5 space-y-4" aria-label="Chi tiết RBAC">
        {tab === 'roles' ? <>
          <h2>{selected.roleId ? `Sửa ${selected.name}` : 'Tạo vai trò mới'}</h2>
          <form className="grid gap-4" onSubmit={saveRole}>
            <label htmlFor="rbac-code">Mã vai trò<input id="rbac-code" className="border rounded-lg p-2 block w-full" required minLength={2} maxLength={50} pattern="[A-Za-z][A-Za-z0-9_]{1,49}" disabled={busy || (selected.roleId && !selected.canRename)} value={form.name} onChange={event => field('name', event.target.value)} /></label>
            <label htmlFor="rbac-display">Tên hiển thị<input id="rbac-display" className="border rounded-lg p-2 block w-full" maxLength={150} disabled={busy} value={form.displayName} onChange={event => field('displayName', event.target.value)} /></label>
            <label htmlFor="rbac-description">Mô tả<textarea id="rbac-description" className="border rounded-lg p-2 block w-full" maxLength={1000} disabled={busy} value={form.description} onChange={event => field('description', event.target.value)} /></label>
            <button className="pill-button navy" disabled={busy}>Lưu vai trò</button>
          </form>
          {selected.roleId && <><fieldset disabled={busy} className="grid gap-2"><legend className="font-semibold">Permission của vai trò</legend>{permissions.map(permission => <label key={permission.permissionId} className="flex gap-2"><input type="checkbox" checked={chosen.includes(permission.permissionId)} onChange={() => setChosen(ids => toggle(ids, permission.permissionId))} />{permission.code}</label>)}</fieldset><button className="pill-button navy" disabled={busy} onClick={savePermissions}>Lưu quyền</button>
            <button className="pill-button" disabled={busy || !selected.canDelete} onClick={removeRole}>Xóa vai trò</button><p className="text-sm">Role hệ thống không đổi mã/xóa. Role còn người dùng phải được thu hồi khỏi tài khoản trước khi xóa.</p></>}
        </> : <><h2>Vai trò của {selected.email}</h2><p>Trạng thái: {selected.status}</p>
          <fieldset disabled={busy} className="grid gap-2"><legend className="font-semibold">Các vai trò được gán</legend>{catalog.map(role => <label className="flex gap-2" key={role.roleId}><input type="checkbox" checked={chosen.includes(role.roleId)} onChange={() => setChosen(ids => toggle(ids, role.roleId))} />{role.displayName} ({role.name})</label>)}</fieldset>
          <button className="pill-button navy" disabled={busy} onClick={saveUserRoles}>Lưu vai trò tài khoản</button>
          {selected.canChangeStatus && <button className="pill-button" disabled={busy} onClick={changeStatus}>{selected.status === 'ACTIVE' ? 'Khóa tài khoản' : 'Mở khóa tài khoản'}</button>}
        </>}
        <button className="pill-button" disabled={busy} onClick={() => setSelected(null)}>Đóng chi tiết</button>{busy && <p role="status">Đang lưu...</p>}
      </section>}
    </>}
  </div></LearnerTemplate>;
}
