import { useState } from 'react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { useAdminReport } from '@/features/admin/useAdminReport';
import { examDraftApi as api, documentUnits, toDraftDocument, unitKey } from '@/features/assessment/exam-drafts.api';

const empty = () => ({ title: '', description: '', testType: 'FULL', durationSeconds: '' });
export default function TeacherTestsPage() {
  const [page, setPage] = useState(0), [revision, setRevision] = useState(0);
  const [query, setQuery] = useState({ partNumber: 1, page: 0 });
  const [draft, setDraft] = useState(null), [editor, setEditor] = useState(empty), [selected, setSelected] = useState([]);
  const [busy, setBusy] = useState(false), [stale, setStale] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('');
  const list = useAdminReport(api.list, page, true, revision);
  const bank = useAdminReport(api.units, query, true);
  const editable = !draft || draft.status === 'DRAFT' && draft.document;
  function reset() { setDraft(null); setEditor(empty()); setSelected([]); setError(''); setNotice(''); setStale(false); }
  async function read(id) {
    setBusy(true); setError(''); setNotice('');
    try {
      const value = await api.read(id); setDraft(value); setStale(false);
      setEditor(value.document ? { ...value.document, description: value.document.description ?? '', durationSeconds: value.document.durationSeconds ?? '' } : empty());
      setSelected(value.document ? documentUnits(value.document) : []);
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  async function save(event) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try {
      const document = toDraftDocument(editor, selected);
      const value = draft ? await api.update(draft, document) : await api.create(document);
      setDraft(value); setRevision(n => n + 1); setNotice('Đã lưu bản nháp.');
    } catch (e) {
      setError(e.code === 'TEST_REVISION_CONFLICT' ? 'Bộ đề đã thay đổi ở phiên khác. Đọc lại để tiếp tục; nội dung đang sửa chưa được ghi.' : e.message);
      if (e.status === 409) setStale(true);
    } finally { setBusy(false); }
  }
  function move(index, delta) {
    const other = index + delta;
    if (other < 0 || other >= selected.length || selected[index].partNumber !== selected[other].partNumber) return;
    setSelected(values => { const next = [...values]; [next[index], next[other]] = [next[other], next[index]]; return next; });
  }
  return <LearnerTemplate><div className="space-y-5">
    <h1>Biên soạn bộ đề</h1>
    <p>Bản nháp có thể chưa đủ câu hoặc thời lượng. Duyệt, xuất bản, ngừng xuất bản và phát audio chưa có trong luồng này.</p>
    {(error || list.error) && <p role="alert">{error || list.error}</p>}
    {notice && <p role="status">{notice}</p>}
    <section className="border rounded-lg p-4 space-y-3" aria-label="Danh sách bộ đề">
      <button className="pill-button" disabled={busy} onClick={reset}>Bộ đề mới</button>
      {list.loading ? <p>Đang tải bộ đề…</p> : <ul>{list.data?.map(row => <li key={row.testId}><button disabled={busy} onClick={() => read(row.testId)}>{row.title} · {row.status}</button></li>)}</ul>}
      <button disabled={page === 0 || busy} onClick={() => setPage(n => n - 1)}>Trang trước</button>
      <button disabled={list.loading || (list.data?.length ?? 0) < 50 || busy} onClick={() => setPage(n => n + 1)}>Trang sau</button>
    </section>
    {draft && <p>Phiên bản {draft.version} · {draft.status} · Lần sửa {draft.revision}</p>}
    {draft && <button disabled={busy} onClick={() => read(draft.testId)}>Đọc lại bản trên máy chủ</button>}
    {!editable && <p>Phiên bản này chỉ đọc. Không sửa dữ liệu của phiên bản đã xuất bản hoặc đã được sử dụng.</p>}
    <form onSubmit={save} className="border rounded-lg p-4 space-y-3">
      <fieldset disabled={busy || !editable || stale || !!list.error} className="space-y-3">
        <label className="block">Tên bộ đề<input className="block border rounded p-2 w-full" required maxLength={255} value={editor.title} onChange={e => setEditor({ ...editor, title: e.target.value })} /></label>
        <label className="block">Mô tả<textarea className="block border rounded p-2 w-full" maxLength={10000} value={editor.description} onChange={e => setEditor({ ...editor, description: e.target.value })} /></label>
        <label className="block" htmlFor="exam-type">Loại bộ đề</label><select id="exam-type" value={editor.testType} onChange={e => setEditor({ ...editor, testType: e.target.value })}>
          <option value="FULL">Listening và Reading</option><option value="LISTENING">Listening</option><option value="READING">Reading</option><option value="PRACTICE">Tự chọn</option>
        </select>
        <label className="block">Thời lượng (giây, tùy chọn)<input type="number" min="1" step="1" value={editor.durationSeconds} onChange={e => setEditor({ ...editor, durationSeconds: e.target.value })} /></label>
        <h2>Câu hỏi đã chọn</h2>
        <ol>{selected.map((unit, index) => <li key={unitKey(unit)} className="border rounded p-2">
          Part {unit.partNumber} · {unit.questions.length} câu · {unit.questions[0].questionText || `Mục ${index + 1}`}
          <button type="button" disabled={index === 0 || selected[index - 1].partNumber !== unit.partNumber} aria-label={`Đưa mục ${index + 1} lên`} onClick={() => move(index, -1)}>Lên</button>
          <button type="button" disabled={index === selected.length - 1 || selected[index + 1].partNumber !== unit.partNumber} aria-label={`Đưa mục ${index + 1} xuống`} onClick={() => move(index, 1)}>Xuống</button>
          <button type="button" aria-label={`Bỏ mục ${index + 1}`} onClick={() => setSelected(values => values.filter((_, i) => i !== index))}>Bỏ cả mục</button>
        </li>)}</ol>
        <button className="pill-button" type="submit">{busy ? 'Đang lưu…' : 'Lưu bản nháp'}</button>
      </fieldset>
    </form>
    <section className="border rounded-lg p-4 space-y-3" aria-label="Chọn từ ngân hàng câu hỏi">
      <h2>Chọn câu hỏi hoặc nguyên nhóm đã xuất bản</h2>
      <label htmlFor="exam-part">Part</label><select id="exam-part" value={query.partNumber} onChange={e => setQuery({ partNumber: Number(e.target.value), page: 0 })}>{[1,2,3,4,5,6,7].map(n => <option key={n}>{n}</option>)}</select>
      {bank.error && <p role="alert">{bank.error}</p>}
      {bank.loading ? <p>Đang tải câu hỏi…</p> : <ul>{bank.data?.map(unit => <li key={unitKey(unit)} className="border rounded p-2">
        <p>Part {unit.partNumber} · {unit.questions.length} câu</p>
        <ol>{unit.questions.map(q => <li key={q.questionVersionId}>{q.questionText}</li>)}</ol>
        <button type="button" disabled={busy || !editable || stale || !!list.error || selected.some(u => unitKey(u) === unitKey(unit))} onClick={() => setSelected(values => [...values, unit].sort((a,b) => a.partNumber-b.partNumber))}>Thêm nguyên mục</button>
      </li>)}</ul>}
      {!bank.loading && bank.data?.length === 0 && <p>Chưa có câu hỏi phù hợp trong Part này.</p>}
      <button disabled={query.page === 0 || bank.loading} onClick={() => setQuery(q => ({ ...q, page: q.page - 1 }))}>Nhóm trước</button>
      <button disabled={bank.loading || (bank.data?.length ?? 0) < 50} onClick={() => setQuery(q => ({ ...q, page: q.page + 1 }))}>Nhóm sau</button>
    </section>
  </div></LearnerTemplate>;
}
