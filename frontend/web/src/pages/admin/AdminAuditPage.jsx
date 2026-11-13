import { useState } from 'react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { auditActions, monitoringApi } from '@/features/admin/monitoring.api';
import { useAdminReport } from '@/features/admin/useAdminReport';

const initial = { actor: '', action: '', entityType: '', entityId: '', from: '', to: '', sort: 'createdAt,desc' };
export default function AdminAuditPage() {
  const { user, restore } = useAuth();
  const allowed = user?.adminCapabilities?.auditRead === true;
  const [draft, setDraft] = useState(initial);
  const [query, setQuery] = useState({ ...initial, page: 0, size: 20 });
  const [revision, setRevision] = useState(0);
  const report = useAdminReport(monitoringApi.audit, query, allowed, revision);
  const [detail, setDetail] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const field = (name, value) => setDraft(previous => ({ ...previous, [name]: value }));
  async function inspect(id) {
    setBusy(true); setDetail(null); setError('');
    try { setDetail(await monitoringApi.detail(id)); }
    catch (err) { setError(err.message); if (err.status === 403) await restore({ background: true }); }
    finally { setBusy(false); }
  }
  function page(delta) { setDetail(null); setQuery(previous => ({ ...previous, page: previous.page + delta })); }
  return <LearnerTemplate><div className="max-w-6xl mx-auto space-y-5">
    <div className="page-heading"><h1>Audit quản trị</h1><p>Các sự kiện đã được server ghi nhận; đây không phải lịch sử đầy đủ mọi hoạt động người dùng.</p></div>
    {!allowed ? <p role="alert" className="form-error">Bạn không có quyền đọc audit.</p> : <>
      <form className="surface p-5 grid gap-4 md:grid-cols-2" onSubmit={event => { event.preventDefault(); setDetail(null); setError(''); setQuery({ ...draft, page: 0, size: 20 }); }}>
        <label htmlFor="audit-actor">Actor UUID<input id="audit-actor" className="border rounded-lg p-2 block w-full" value={draft.actor} onChange={event => field('actor', event.target.value)} /></label>
        <label htmlFor="audit-action">Hành động<select id="audit-action" className="border rounded-lg p-2 block w-full" value={draft.action} onChange={event => field('action', event.target.value)}><option value="">Tất cả</option>{auditActions.map(action => <option key={action}>{action}</option>)}</select></label>
        <label htmlFor="audit-type">Loại tài nguyên<select id="audit-type" className="border rounded-lg p-2 block w-full" value={draft.entityType} onChange={event => field('entityType', event.target.value)}><option value="">Tất cả</option><option>USER</option><option>ROLE</option><option>SYSTEM_SETTING</option><option>OTHER</option></select></label>
        <label htmlFor="audit-resource">ID tài nguyên<input id="audit-resource" className="border rounded-lg p-2 block w-full" value={draft.entityId} onChange={event => field('entityId', event.target.value)} /><small>UUID; ROLE dùng roleId; SYSTEM_SETTING dùng key đã chốt.</small></label>
        <label htmlFor="audit-from">Từ (UTC, gồm mốc)<input id="audit-from" className="border rounded-lg p-2 block w-full" placeholder="2026-11-13T00:00:00Z" value={draft.from} onChange={event => field('from', event.target.value)} /></label>
        <label htmlFor="audit-to">Đến (UTC, không gồm mốc)<input id="audit-to" className="border rounded-lg p-2 block w-full" placeholder="2026-11-14T00:00:00Z" value={draft.to} onChange={event => field('to', event.target.value)} /></label>
        <label htmlFor="audit-sort">Sắp xếp<select id="audit-sort" className="border rounded-lg p-2 block w-full" value={draft.sort} onChange={event => field('sort', event.target.value)}><option value="createdAt,desc">Mới nhất</option><option value="createdAt,asc">Cũ nhất</option><option value="action,asc">Hành động A–Z</option><option value="action,desc">Hành động Z–A</option></select></label>
        <div className="flex gap-3 items-end"><button className="pill-button navy" disabled={report.loading || busy}>Lọc audit</button><button className="pill-button" type="button" disabled={report.loading || busy} onClick={() => { setDetail(null); setRevision(value => value + 1); }}>Làm mới</button></div>
      </form>
      {(report.error || error) && <p role="alert" className="form-error">{report.error || error}</p>}
      {report.loading && <LoadingSpinner text="Đang tải audit..." />}
      {report.data && <section className="surface p-5 space-y-4"><p>Tìm thấy {report.data.totalElements} bản ghi</p><div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr>{['Thời điểm UTC','Actor UUID','Hành động','Tài nguyên','Chi tiết'].map(label => <th className="p-2" key={label}>{label}</th>)}</tr></thead><tbody>{report.data.items.map(event => <tr key={event.auditId} className="border-t"><td className="p-2">{event.createdAt}</td><td className="p-2 break-all">{event.actorUserId || 'Không ghi nhận actor'}</td><td className="p-2">{event.action}</td><td className="p-2 break-all">{event.entityType} · {event.entityId || 'Không có ID'}</td><td className="p-2"><button className="pill-button" disabled={busy} onClick={() => inspect(event.auditId)}>Xem chi tiết</button></td></tr>)}</tbody></table></div>
        {!report.data.items.length && <p>Chưa có bản ghi phù hợp.</p>}
        <div className="flex gap-4 items-center"><button className="pill-button" disabled={busy || !query.page} onClick={() => page(-1)}>Trang trước</button><span>Trang {report.data.page + 1}</span><button className="pill-button" disabled={busy || (report.data.page + 1) * report.data.size >= report.data.totalElements} onClick={() => page(1)}>Trang sau</button></div>
      </section>}
      {busy && <LoadingSpinner text="Đang tải chi tiết..." />}
      {detail && <section className="surface p-5 space-y-3" aria-label="Chi tiết audit"><h2>{detail.event.action}</h2><p>ID audit: {detail.event.auditId}</p><p>Tài nguyên: {detail.event.entityType} · {detail.event.entityId}</p><p>Thời điểm UTC: {detail.event.createdAt}</p><p>Chỉ hiển thị trạng thái, ID role/permission, tên trường thay đổi và giá trị số/phiên bản của hai cấu hình được phép. Tên, mô tả và payload nguyên văn được ẩn.</p><pre className="overflow-auto bg-slate-50 p-4 rounded-lg">{JSON.stringify(detail.changes, null, 2)}</pre><button className="pill-button" onClick={() => setDetail(null)}>Đóng chi tiết</button></section>}
    </>}
  </div></LearnerTemplate>;
}
