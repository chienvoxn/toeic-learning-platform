import { useState } from 'react';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { contentManagementApi as api } from '@/features/admin/content-management.api';
import { useAdminReport } from '@/features/admin/useAdminReport';
import { editorPayload } from '@/features/admin/content-editor';

const categories = { GRAMMAR: 'Ngữ pháp', VOCAB: 'Từ vựng', LISTENING: 'Nghe', READING: 'Đọc', STRATEGIES: 'Chiến lược', DOCS: 'Tài liệu' };
export default function ContentEditor({ kind, initial, source, saved, cancelled, failed, pending }) {
  const [state, setState] = useState(initial); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const options = useAdminReport(api.options, null, kind === 'questions');
  const field = (name, value) => setState(previous => ({ ...previous, [name]: value }));
  const parent = source.parent; const id = parent?.contentId || parent?.questionId || parent?.groupId;
  const number = options.data?.parts.find(part => String(part.partId) === state.partId)?.partNumber;
  function part(value) {
    const count = options.data.parts.find(row => String(row.partId) === value)?.partNumber === 2 ? 3 : 4;
    setState(previous => ({ ...previous, partId: value, choices: Array.from({ length: count }, (_, i) => ({ label: String.fromCharCode(65 + i), content: previous.choices[i]?.content || '', isCorrect: previous.choices[i]?.isCorrect || false, displayOrder: i + 1 })) }));
  }
  function choice(index, name, value) { field('choices', state.choices.map((row, i) => ({ ...row, [name]: name === 'isCorrect' ? i === index : i === index ? value : row[name] }))); }
  async function submit(event) {
    event.preventDefault(); setError(''); let payload;
    try { payload = editorPayload(kind, state, initial, source, number); } catch (err) { setError(err.message); return; }
    setBusy(true); pending(true);
    try { const result = parent ? await api.update(kind, id, payload) : await api.create(kind, payload); saved(result); }
    catch (err) { setError(err.message); await failed(err); }
    finally { setBusy(false); pending(false); }
  }
  const text = (name, label, multiline = false, required = false, maxLength) => <label className="grid gap-1" key={name} htmlFor={'edit-' + name}>{label}{multiline ? <textarea id={'edit-' + name} className="border rounded-lg p-2" value={state[name]} onChange={event => field(name, event.target.value)} required={required} /> : <input id={'edit-' + name} className="border rounded-lg p-2" value={state[name]} onChange={event => field(name, event.target.value)} required={required} maxLength={maxLength} />}</label>;
  return <form aria-label="Soạn nội dung" className="surface p-5 space-y-4" onSubmit={submit}>
    <h2>{parent ? 'Sửa bản nháp' : 'Tạo nội dung mới'}</h2>
    <p>Lưu chỉ tạo/sửa DRAFT. Nội dung đã publish giữ nguyên; API tạo hoặc dùng lại bản nháp từ đúng published pointer.</p>
    {parent?.status === 'PUBLISHED' && <p>Đang soạn phiên bản mới, không thay bản đang hiển thị cho người học.</p>}
    {options.loading && <LoadingSpinner text="Đang tải danh mục Part/chủ đề..." />}{options.error && <p role="alert">{options.error}</p>}
    <fieldset disabled={busy || (kind === 'questions' && !options.data)} className="grid gap-4">
      {kind === 'contents' ? <>{text('title','Tiêu đề',false,true,255)}{text('summary','Tóm tắt',true)}{text('body','Nội dung',true)}
        <label htmlFor="edit-categoryCode">Loại học liệu<select id="edit-categoryCode" disabled={!source.metadata} value={state.categoryCode} onChange={event => field('categoryCode',event.target.value)}>{Object.entries(categories).map(([code,label]) => <option value={code} key={code}>{label}</option>)}</select></label>
        <label htmlFor="edit-difficulty">Độ khó<input id="edit-difficulty" disabled={!source.metadata} value={state.difficulty} maxLength={20} onChange={event => field('difficulty',event.target.value)} /></label></>
        : kind === 'groups' ? <><label htmlFor="edit-groupType">Loại nhóm (tùy chọn)<input id="edit-groupType" disabled={!source.metadata} value={state.groupType} maxLength={30} onChange={event => field('groupType',event.target.value)} /></label>{text('instruction','Hướng dẫn',true)}{text('passageText','Đoạn văn',true)}</>
          : <><label htmlFor="edit-partId">Part<select id="edit-partId" disabled={!source.metadata} value={state.partId} required onChange={event => part(event.target.value)}><option value="">Chọn Part</option>{options.data?.parts.map(row => <option value={row.partId} key={row.partId}>Part {row.partNumber} — {row.name}</option>)}</select></label>
            <label htmlFor="edit-topicId">Chủ đề<select id="edit-topicId" disabled={!source.metadata} value={state.topicId} onChange={event => field('topicId',event.target.value)}><option value="">Không chọn</option>{options.data?.topics.map(row => <option value={row.topicId} key={row.topicId}>{row.name}</option>)}</select></label>
            {text('groupVersionId','ID phiên bản nhóm (Part 3/4/6/7)')}<p>ID phiên bản được hiển thị khi xem lịch sử nhóm; không tự chọn phiên bản mới nhất. Dạng câu SINGLE_CHOICE.</p>
            {text('questionText','Câu hỏi',true,true)}{text('explanation','Giải thích',true)}
            <label htmlFor="edit-difficulty">Độ khó<select id="edit-difficulty" value={state.difficulty} onChange={event => field('difficulty',event.target.value)}><option value="">Không chọn</option>{['EASY','MEDIUM','HARD'].map(value => <option key={value}>{value}</option>)}</select></label>
            <fieldset className="space-y-2"><legend>Lựa chọn và đáp án đúng</legend>{state.choices.map((row,index) => <div className="flex gap-3" key={row.label}><label htmlFor={'choice-' + row.label}>{row.label}<input id={'choice-' + row.label} required value={row.content} onChange={event => choice(index,'content',event.target.value)} /></label><label><input type="radio" name="correctChoice" checked={row.isCorrect} onChange={() => choice(index,'isCorrect',true)} />Đúng {row.label}</label></div>)}</fieldset></>}
      <label><input type="checkbox" checked={state.replaceAssets} onChange={event => field('replaceAssets',event.target.checked)} /> Thay toàn bộ liên kết media của bản nháp</label>
      {!state.replaceAssets && <p>Giữ nguyên các liên kết hiện có. Server kiểm lại mọi quyền media, không âm thầm bỏ liên kết bị hạn chế.</p>}
      {state.replaceAssets && <div className="space-y-3"><p>Danh sách trống bỏ toàn bộ liên kết nháp. Media cần quyền owner/grant và visibility hợp lệ; không upload hoặc nhập URL. Với câu/nhóm, transcript dưới đây thay transcript của liên kết được gửi.</p>
        {state.assets.map((row,index) => <div className="border p-3 space-y-2" key={index}><label htmlFor={'media-' + index}>Media UUID<input id={'media-' + index} value={row.mediaId} onChange={event => field('assets',state.assets.map((asset,i) => i === index ? { ...asset,mediaId:event.target.value } : asset))} /></label>
          {kind !== 'contents' && <label htmlFor={'transcript-' + index}>Transcript của liên kết<textarea id={'transcript-' + index} value={row.transcript} onChange={event => field('assets',state.assets.map((asset,i) => i === index ? { ...asset,transcript:event.target.value } : asset))} /></label>}
          <button type="button" className="pill-button" onClick={() => field('assets',state.assets.filter((_,i) => i !== index))}>Bỏ media {index + 1}</button></div>)}
        <button type="button" className="pill-button" onClick={() => field('assets',[...state.assets,{ mediaId:'',transcript:'' }])}>Thêm media</button>
      </div>}
      <button className="pill-button navy" disabled={busy}>{busy ? 'Đang lưu...' : 'Lưu bản nháp'}</button>
    </fieldset>
    <button type="button" className="pill-button" disabled={busy} onClick={() => { if(JSON.stringify(state) === JSON.stringify(initial) || window.confirm('Bỏ thay đổi chưa lưu trong biểu mẫu?'))cancelled(); }}>Đóng biểu mẫu</button>
    {error && <p role="alert" className="form-error">{error}</p>}
  </form>;
}
