import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { vocabularyRepo as repo } from '@/features/vocabulary/repositories/vocabulary.repo';

// Chỉ lưu sau xác nhận HTTP; tải DTO gốc để PUT không làm mất metadata chưa sửa.
export default function VocabularyActions({ deck, word, onSaved }) {
  const navigate = useNavigate();
  const [mode, setMode] = useState('');
  const [draft, setDraft] = useState(null);
  const [history, setHistory] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function run(action) {
    setBusy(true); setError('');
    try { await action(); } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }
  function edit() {
    run(async () => {
      if (word) {
        const dto = await repo.getVocabulary(word.id);
        const { word: text, meaning, partOfSpeech, pronunciation, pronunciationAudioResourceId, example, note } = dto;
        setDraft({ word: text, meaning, partOfSpeech, pronunciation, pronunciationAudioResourceId, example, note });
      } else setDraft({ name: deck.title, description: deck.description || '' });
      setMode('edit');
    });
  }
  function save(event) {
    event.preventDefault();
    run(async () => {
      if (word) await repo.updateVocabulary(word.id, draft);
      else await repo.updateDeck(deck.id, draft);
      setMode(''); onSaved();
    });
  }
  function remove() {
    run(async () => {
      if (mode === 'detach') await repo.removeFromDeck(deck.id, word.id);
      else if (word) await repo.deleteVocabulary(word.id);
      else await repo.deleteDeck(deck.id);
      setMode('');
      if (!word) navigate('/vocabulary'); else onSaved();
    });
  }
  return <div className="space-y-3 mt-3">
    <div className="flex flex-wrap gap-3">
      <button type="button" className="pill-button" disabled={busy} onClick={edit}>Sửa {word ? 'từ' : 'bộ thẻ'}</button>
      {word && <><button type="button" className="pill-button" disabled={busy} onClick={() => { setError(''); setMode('detach'); }}>Gỡ khỏi bộ</button>
        <button type="button" className="pill-button" disabled={busy} onClick={() => run(async () => { setHistory(await repo.getReviewHistory(word.id)); setMode('history'); })}>Lịch sử ôn</button></>}
      <button type="button" className="pill-button" disabled={busy} onClick={() => { setError(''); setMode('delete'); }}>Xóa {word ? 'từ' : 'bộ thẻ'}</button>
    </div>
    {error && <p role="alert" className="form-error">{error}</p>}
    {busy && <p role="status">Đang xử lý...</p>}
    {mode === 'edit' && draft && <form onSubmit={save} className="surface p-4 space-y-3">
      {(word ? [['word', 'Từ tiếng Anh', 150], ['meaning', 'Nghĩa tiếng Việt'], ['partOfSpeech', 'Từ loại', 40], ['pronunciation', 'Phiên âm', 255], ['example', 'Ví dụ'], ['note', 'Ghi chú']] : [['name', 'Tên bộ thẻ', 150], ['description', 'Mô tả']]).map(([key, label, max]) => <label className="block" key={key}>{label}<input className="w-full border rounded-lg p-2" disabled={busy} maxLength={max} required={['word', 'meaning', 'name'].includes(key)} value={draft[key] || ''} onChange={event => setDraft({ ...draft, [key]: event.target.value })} /></label>)}
      {word && <p className="text-sm">Thay đổi áp dụng cho từ này trong tất cả bộ thẻ của bạn.</p>}
      <button type="submit" className="pill-button navy" disabled={busy}>Lưu thay đổi</button>
    </form>}
    {['delete', 'detach'].includes(mode) && <div className="surface p-4" role="alert">
      <p>{mode === 'detach' ? 'Gỡ từ khỏi bộ này? Từ vẫn còn trong sổ tay.' : word ? 'Xóa từ khỏi sổ tay, các bộ thẻ và lịch sử ôn liên quan? Không thể hoàn tác.' : 'Xóa bộ thẻ này? Các từ trong sổ tay vẫn được giữ lại. Không thể hoàn tác.'}</p>
      <button type="button" className="pill-button navy mt-3" disabled={busy} onClick={remove}>Xác nhận</button>
    </div>}
    {mode === 'history' && history && <section className="surface p-4"><h3 className="font-bold">Lịch sử ôn: {word.word}</h3>
      {!history.length ? <p>Chưa có lượt ôn.</p> : <ul>{history.map(item => <li key={item.reviewHistoryId}>{new Date(item.reviewedAt).toLocaleString('vi-VN')} · {['', 'Học lại', 'Khó', 'Tốt', 'Dễ'][item.rating]} · Khoảng ôn mới: {item.newInterval} ngày</li>)}</ul>}
    </section>}
    {mode && <button type="button" className="pill-button" disabled={busy} onClick={() => { setMode(''); setError(''); }}>Đóng / Hủy</button>}
  </div>;
}
