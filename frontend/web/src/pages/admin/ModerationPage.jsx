import { useState } from 'react';
import { Link } from 'react-router-dom';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { useAdminReport } from '@/features/admin/useAdminReport';
import { moderationApi as api } from '@/features/admin/moderation.api';

const names = { contents: 'Học liệu', questions: 'Câu hỏi', groups: 'Nhóm' };
const actions = { submit: 'Gửi duyệt', approve: 'Duyệt', return: 'Trả sửa', revoke: 'Thu hồi duyệt', publish: 'Xuất bản' };
const load = async (query, signal) => ({ ...await api.list(query, signal), kind: query.kind });
export default function ModerationPage() {
  const { restore } = useAuth(); const [query, setQuery] = useState({ kind: 'contents', page: 0 });
  const [refresh, setRefresh] = useState(0); const [detail, setDetail] = useState(null); const [busy, setBusy] = useState(false);
  const [error, setError] = useState(''); const [notice, setNotice] = useState(''); const [reason, setReason] = useState(''); const [visibility, setVisibility] = useState('');
  const caps = useAdminReport(api.capabilities, null, true, refresh); const scope = caps.data?.[query.kind];
  const allowed = scope && Object.values(scope).some(Boolean); const report = useAdminReport(load, query, !!allowed, refresh);
  const data = report.data?.kind === query.kind && report.data?.page === query.page ? report.data : null;
  async function failed(err) { setError(err.message); if (err.status === 403) await restore({ background: true }); }
  async function inspect(row) {
    setBusy(true); setError(''); setNotice(''); setDetail(null); setReason(''); setVisibility('');
    try { setDetail(await api.detail(query.kind, row.parentId, row.versionId)); } catch (err) { await failed(err); } finally { setBusy(false); }
  }
  async function command(action) {
    setError(''); const body = { expectedRevision: detail.revision };
    if (['return', 'revoke'].includes(action)) { if (!reason.trim() || reason.length > 2000) { setError('Nhập lý do 1–2000 ký tự.'); return; } body.reason = reason; }
    if (action === 'publish' && query.kind === 'contents') { if (!visibility) { setError('Chọn visibility trước khi xuất bản.'); return; } body.visibility = visibility; }
    if (!window.confirm(actions[action] + ' đúng phiên bản và revision đang xem?')) return;
    setBusy(true); setNotice('');
    try { setDetail(await api.transition(query.kind, detail.parentId, detail.versionId, action, body)); setNotice('Đã lưu transition.'); setRefresh(value => value + 1); setReason(''); }
    catch (err) { await failed(err); if (err.status === 409) setDetail(null); }
    finally { setBusy(false); }
  }
  return <LearnerTemplate><div className="max-w-6xl mx-auto space-y-5">
    <div className="page-heading"><h1>Duyệt và xuất bản</h1><p>Approval gắn đúng version/revision. Nhóm và câu thuộc nhóm chưa hỗ trợ xuất bản; bộ đề chưa tích hợp.</p></div>
    <Link className="pill-button" to="/teacher/content">Soạn học liệu và câu hỏi</Link>
    <label htmlFor="moderation-kind">Loại<select id="moderation-kind" disabled={busy} value={query.kind} onChange={e => { setDetail(null); setQuery({ kind: e.target.value, page: 0 }); }}>{Object.entries(names).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
    {(caps.loading || report.loading || busy) && <p>Đang tải...</p>}
    {(error || caps.error || report.error) && <p role="alert">{error || caps.error || report.error}</p>}{notice && <p role="status">{notice}</p>}
    {scope && !allowed && <p role="alert">Bạn không có quyền xem loại nội dung này.</p>}
    {allowed && data && <section className="surface p-5"><p>Tổng {data.totalElements} phiên bản</p><table className="w-full"><thead><tr><th>Version</th><th>Trạng thái</th><th>Revision</th><th /></tr></thead><tbody>{data.items.map(row => <tr key={row.versionId}><td className="break-all">{row.versionId}</td><td>{row.status}</td><td>{row.revision ?? 'Legacy'}</td><td><button disabled={busy || report.loading} className="pill-button" onClick={() => inspect(row)}>Xem duyệt</button></td></tr>)}</tbody></table>
      {!data.items.length && <p>Chưa có phiên bản.</p>}
      <button disabled={busy || !query.page} onClick={() => { setDetail(null); setQuery(q => ({ ...q, page: q.page - 1 })); }}>Trang trước</button><span> Trang {query.page + 1} </span><button disabled={busy || (query.page + 1) * 20 >= data.totalElements} onClick={() => { setDetail(null); setQuery(q => ({ ...q, page: q.page + 1 })); }}>Trang sau</button>
    </section>}
    {detail && <section aria-label="Chi tiết duyệt" className="surface p-5 space-y-3"><h2>{detail.status} · revision {detail.revision ?? 'legacy'}</h2><p className="break-all">Version: {detail.versionId} · Nguồn: {detail.sourceVersionId || 'Tạo mới'}</p>
      {!detail.evidenceComplete && <p role="alert">Thiếu bằng chứng contributor của vòng hiện tại, chưa thể chuyển trạng thái.</p>}
      <pre className="whitespace-pre-wrap break-all">{JSON.stringify(detail.payload, null, 2)}</pre>
      {(detail.capabilities.return || detail.capabilities.revoke) && <label htmlFor="moderation-reason">Lý do<textarea id="moderation-reason" maxLength={2000} disabled={busy} value={reason} onChange={e => setReason(e.target.value)} /></label>}
      {detail.capabilities.publish && query.kind === 'contents' && <label htmlFor="moderation-visibility">Visibility<select id="moderation-visibility" disabled={busy} value={visibility} onChange={e => setVisibility(e.target.value)}><option value="">Chọn visibility</option><option>PUBLIC</option><option>AUTHENTICATED</option></select></label>}
      <div>{Object.entries(actions).filter(([key]) => detail.capabilities[key]).map(([key, label]) => <button className="pill-button" key={key} disabled={busy} onClick={() => command(key)}>{label}</button>)}</div>
      <ul>{detail.decisions.map((decision, i) => <li key={i}>{decision.action} · revision {decision.revision}{decision.reason && <p>{decision.reason}</p>}</li>)}</ul>
      <p>Media thiếu quyền không được bỏ qua. Lỗi stale cần mở lại phiên bản để kiểm tra trước khi thao tác.</p>
    </section>}
  </div></LearnerTemplate>;
}
