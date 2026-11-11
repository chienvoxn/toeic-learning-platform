import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { vocabularyRepo } from '@/features/vocabulary/repositories/vocabulary.repo';

export default function DeckEditor() {
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError('');
    try { const deck = await vocabularyRepo.createDeck(name, description); navigate(`/vocabulary/${deck.deckId}`); }
    catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }
  return <section className="surface p-5 mb-6"><h2 className="font-bold mb-3">Tạo bộ thẻ của tôi</h2>
    {error && <p className="form-error" role="alert">{error}</p>}
    <form onSubmit={submit} className="space-y-3"><label className="block">Tên bộ thẻ<input className="w-full border p-2 rounded-lg" maxLength={150} required value={name} onChange={event => setName(event.target.value)} /></label><label className="block">Mô tả<input className="w-full border p-2 rounded-lg" value={description} onChange={event => setDescription(event.target.value)} /></label><button type="submit" disabled={busy} className="pill-button navy">{busy ? 'Đang tạo...' : 'Tạo bộ thẻ'}</button></form>
  </section>;
}
