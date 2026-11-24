import { Fragment, useState } from 'react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { useAdminReport } from '@/features/admin/useAdminReport';
import { contentManagementApi as api } from '@/features/admin/content-management.api';
import { initialEditor, workingSource } from '@/features/admin/content-editor';
import ContentEditor from './ContentEditor';
import { Link, useLocation } from 'react-router-dom';
import { moderationApi } from '@/features/admin/moderation.api';

const types = { contents: 'Học liệu', questions: 'Câu hỏi', groups: 'Nhóm câu hỏi' };
const load = async (query, signal) => ({ ...await api.list(query.kind, query, signal), sourceKind: query.kind, sourceStatus: query.status });
const idOf = (kind, row) => row[kind === 'contents' ? 'contentId' : kind === 'questions' ? 'questionId' : 'groupId'];
function Versions({ kind, detail }) {
  return detail.versions.map(version => <article key={version.contentVersionId || version.questionVersionId || version.groupVersionId} className="border rounded-lg p-4 space-y-2">
    <h3>Phiên bản {version.versionNumber} · {version.status}</h3><p className="text-sm break-all">ID phiên bản: {version.contentVersionId || version.questionVersionId || version.groupVersionId}</p>
    {kind === 'contents' ? <><h4>{version.title}</h4><p className="whitespace-pre-wrap">{version.summary}</p><p className="whitespace-pre-wrap">{version.body}</p></>
      : kind === 'groups' ? <><p>{version.instruction}</p><p className="whitespace-pre-wrap">{version.passageText}</p></>
        : <><p className="whitespace-pre-wrap">{version.questionText}</p>{version.passage && <p className="whitespace-pre-wrap">{version.passage.text || version.passage.passageText}</p>}
          <ul>{version.choices.map(choice => <li key={choice.choiceId}>{choice.label}. {choice.content}{choice.isCorrect ? ' (Đáp án đúng)' : ''}</li>)}</ul><p className="whitespace-pre-wrap">Giải thích: {version.explanation}</p></>}
  </article>);
}
export default function AdminContentPage({ embedded = false }) {
  const Layout = embedded ? Fragment : LearnerTemplate;
  const { user, restore } = useAuth();
  const location = useLocation();
  const requestedKind = new URLSearchParams(location.search).get('kind');
  const [query, setQuery] = useState({ kind: Object.hasOwn(types, requestedKind) ? requestedKind : user?.adminCapabilities?.contentManagement ? 'contents' : 'questions', status: '', page: 0, size: 20 });
  const [revision, setRevision] = useState(0);
  const [detail, setDetail] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [editor, setEditor] = useState(null);
  const capabilities = useAdminReport(moderationApi.capabilities, null, true, revision);
  const adminAllowed = query.kind === 'contents' ? user?.adminCapabilities?.contentManagement === true : user?.adminCapabilities?.questionManagement === true;
  const allowed = location.pathname === '/admin/content' ? adminAllowed : capabilities.data?.[query.kind]?.author === true;
  const report = useAdminReport(load, query, allowed, revision);
  const data = report.data?.sourceKind === query.kind && report.data?.sourceStatus === query.status && report.data?.page === query.page ? report.data : null;
  function change(next) { setDetail(null); setNotice(''); setError(''); setQuery(previous => ({ ...previous, ...next })); }
  async function failure(err) { setError(err.message); if (err.status === 403) await restore({ background: true }); }
  async function inspect(row) {
    setError(''); setDetail(null); setBusy(true);
    try { setDetail(await api.detail(query.kind, idOf(query.kind, row))); } catch (err) { await failure(err); }
    finally { setBusy(false); }
  }
  async function archive(row) {
    if (!window.confirm(`Archive ${types[query.kind].toLowerCase()} này? Mục sẽ bị ẩn khỏi truy vấn đang dùng; phiên bản và lịch sử vẫn được giữ.`)) return;
    setBusy(true); setError(''); setNotice(''); setDetail(null);
    try { await api.archive(query.kind, idOf(query.kind, row)); setNotice('Đã archive. Phiên bản và lịch sử được giữ nguyên.'); setRevision(value => value + 1); }
    catch (err) { await failure(err); }
    finally { setBusy(false); }
  }
  async function edit(row) {
    setError(''); setNotice(''); setDetail(null); setBusy(true);
    try {
      const value = row ? await api.detail(query.kind, idOf(query.kind, row)) : null;
      setEditor({ source: workingSource(query.kind, value), initial: initialEditor(query.kind, value) });
    } catch (err) { await failure(err); }
    finally { setBusy(false); }
  }
  function saved(value) { setEditor(null); setDetail(value); setError(''); setNotice('Đã lưu bản nháp. Bản đã publish được giữ nguyên.'); setRevision(previous => previous + 1); }
  return <Layout><div className="max-w-6xl mx-auto space-y-5">
    <div className="page-heading"><h1>Quản lý học liệu và câu hỏi</h1><p>Tạo/sửa bản nháp, xem phiên bản và lưu trữ. Xuất bản nguyên nhóm cần thao tác G1 riêng; màn hình này chưa có nút batch.</p><Link className="pill-button" to="/moderation">Duyệt và xuất bản</Link><Link className="pill-button" to="/teacher/tests">Bộ đề và phiên bản</Link></div>
    <div className="flex flex-wrap gap-4"><label htmlFor="management-kind">Loại<select id="management-kind" disabled={busy || !!editor} value={query.kind} onChange={event => change({ kind: event.target.value, page: 0 })}>{Object.entries(types).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
      <label htmlFor="management-status">Trạng thái<select id="management-status" disabled={busy || !!editor || !allowed} value={query.status} onChange={event => change({ status: event.target.value, page: 0 })}><option value="">Tất cả</option>{['DRAFT','PUBLISHED','ARCHIVED'].map(value => <option key={value}>{value}</option>)}</select></label>
      <button className="pill-button" disabled={busy || !!editor || report.loading || !allowed} onClick={() => { setDetail(null); setError(''); setRevision(value => value + 1); }}>Làm mới danh sách</button>
      <button className="pill-button navy" disabled={busy || !!editor || !allowed} onClick={() => edit(null)}>Tạo bản nháp</button></div>
    {capabilities.loading && <LoadingSpinner text="Đang tải quyền..." />}{capabilities.error && <p role="alert">{capabilities.error}</p>}
    {!allowed ? !capabilities.loading && !capabilities.error && <p role="alert" className="form-error">Bạn không có quyền quản lý loại nội dung này.</p> : <>
      {query.kind === 'groups' && <p>Archive nhóm làm các câu liên quan bị ẩn theo policy đọc hiện có; trạng thái từng câu và snapshot đã lưu được giữ nguyên.</p>}
      {(error || report.error) && <p role="alert" className="form-error">{error || report.error}</p>}{notice && <p role="status">{notice}</p>}
      {(report.loading || busy) && <LoadingSpinner text="Đang tải..." />}
      {editor && <ContentEditor kind={query.kind} {...editor} saved={saved} cancelled={() => setEditor(null)} failed={failure} pending={setBusy} />}
      {data && <section className="surface p-5 space-y-4"><p>Tìm thấy {data.totalElements} mục</p><table className="w-full text-left"><thead><tr>{['Mục','Trạng thái','Thao tác'].map(title => <th key={title}>{title}</th>)}</tr></thead><tbody>{data.content.map(row => <tr key={idOf(query.kind, row)} className="border-t"><td className="p-3 break-all">{row.title || idOf(query.kind, row)}</td><td>{row.status}</td><td><button className="pill-button" disabled={busy || !!editor || report.loading} onClick={() => inspect(row)}>Xem phiên bản</button><button className="pill-button" disabled={busy || !!editor || report.loading || row.status === 'ARCHIVED'} onClick={() => edit(row)}>Sửa bản nháp</button><button className="pill-button" disabled={busy || !!editor || report.loading || row.status === 'ARCHIVED'} onClick={() => archive(row)}>Archive</button></td></tr>)}</tbody></table>
        {!data.content.length && <p>Chưa có nội dung phù hợp.</p>}
        <div className="flex gap-4"><button className="pill-button" disabled={busy || !!editor || !query.page} onClick={() => change({ page: query.page - 1 })}>Trang trước</button><span>Trang {data.page + 1}</span><button className="pill-button" disabled={busy || !!editor || (data.page + 1) * data.size >= data.totalElements} onClick={() => change({ page: query.page + 1 })}>Trang sau</button></div>
      </section>}
      {detail && <section className="surface p-5 space-y-3" aria-label="Phiên bản nội dung"><h2>Phiên bản nội dung</h2><Versions kind={query.kind} detail={detail} /><button className="pill-button" onClick={() => setDetail(null)}>Đóng phiên bản</button></section>}
    </>}
  </div></Layout>;
}
