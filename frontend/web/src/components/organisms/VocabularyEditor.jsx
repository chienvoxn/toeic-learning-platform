import { useState } from 'react';
import { vocabularyRepo } from '@/features/vocabulary/repositories/vocabulary.repo';

export default function VocabularyEditor({ deckId, onSaved }) {
  const [word, setWord] = useState('');
  const [meaning, setMeaning] = useState('');
  const [example, setExample] = useState('');
  const [pendingId, setPendingId] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const vocabularyId = pendingId || (await vocabularyRepo.createVocabulary({ word, meaning, example })).vocabularyId;
      setPendingId(vocabularyId);
      await vocabularyRepo.addToDeck(deckId, vocabularyId);
      setPendingId(null); setWord(''); setMeaning(''); setExample(''); onSaved();
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }
  return <section className="surface p-5 mb-6"><h2 className="font-bold mb-3">Thêm từ vào bộ thẻ</h2>
    {error && <p className="form-error" role="alert">{error}</p>}
    {pendingId && !busy && <p role="status" className="text-sm mb-3">Từ đã được tạo trong sổ tay. Hãy thử lại để thêm từ đó vào bộ thẻ.</p>}
    <form onSubmit={submit} className="space-y-3">
      <label className="block">Từ tiếng Anh<input className="w-full border rounded-lg p-2" required maxLength={150} value={word} disabled={busy || Boolean(pendingId)} onChange={event => setWord(event.target.value)} /></label>
      <label className="block">Nghĩa tiếng Việt<input className="w-full border rounded-lg p-2" required value={meaning} disabled={busy || Boolean(pendingId)} onChange={event => setMeaning(event.target.value)} /></label>
      <label className="block">Câu ví dụ<input className="w-full border rounded-lg p-2" value={example} disabled={busy || Boolean(pendingId)} onChange={event => setExample(event.target.value)} /></label>
      <button className="pill-button navy" disabled={busy} type="submit">{busy ? 'Đang lưu...' : pendingId ? 'Thử thêm lại vào bộ thẻ' : 'Thêm từ'}</button>
    </form>
  </section>;
}
