import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { Volume2, ChevronLeft, CheckCircle2, AlertTriangle } from 'lucide-react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { assetUrl } from '@/lib/figma-assets';
import { vocabularyRepo } from '@/features/vocabulary/repositories/vocabulary.repo';

const ratings = [['AGAIN', 'HỌC LẠI', '10 phút'], ['HARD', 'KHÓ', '1–3 ngày'], ['GOOD', 'TỐT', '1–4 ngày'], ['EASY', 'DỄ', '1–7 ngày']];
export default function VocabReviewPage() {
  const { deckId } = useParams();
  const [data, setData] = useState(null);
  const [current, setCurrent] = useState(0);
  const [revealed, setRevealed] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [completed, setCompleted] = useState(false);
  useEffect(() => { let active = true; Promise.all([vocabularyRepo.getDeckCards(deckId), vocabularyRepo.getDecks()]).then(([cards,decks]) => { if (active) setData({ cards, deck: decks.find(d => d.id === deckId) }); }).catch(err => { if (active) setError(err.message); }); return () => { active = false; }; }, [deckId]);
  async function rate(value) {
    if (saving) return;
    setSaving(true); setError('');
    try { await vocabularyRepo.rateCard(data.cards[current].id, value); if (current + 1 < data.cards.length) { setCurrent(current+1); setRevealed(false); } else setCompleted(true); }
    catch (err) { setError(err.message || 'Chưa lưu được đánh giá. Hãy thử lại.'); }
    finally { setSaving(false); }
  }
  function speak(text) { if ('speechSynthesis' in window) { window.speechSynthesis.cancel(); const speech = new SpeechSynthesisUtterance(text); speech.lang = 'en-US'; speech.rate = .9; window.speechSynthesis.speak(speech); } }
  const card = data?.cards[current];
  return <LearnerTemplate variant="flashcard"><div className="flashcard-page"><Link to="/vocabulary" className="breadcrumb-back"><ChevronLeft size={13} />Quay lại</Link><div className="flashcard-title"><h1>{data?.deck?.title || 'Ôn tập từ vựng'}</h1><span className="pill-button navy">Flashcard</span></div>
    {error && <p className="form-error" role="alert">{error}</p>}{!data && !error && <LoadingSpinner text="Đang tải bộ thẻ..." />}
    {completed ? <div className="review-complete surface"><CheckCircle2 size={56} /><h2>Hoàn thành phiên ôn tập!</h2><p>Bạn đã ôn tập {data.cards.length} từ vựng.</p><Link className="pill-button navy" to="/vocabulary">Quay lại thư viện</Link></div> : data && !card ? <p className="empty-message">Bộ thẻ hiện chưa có từ vựng. <Link to="/vocabulary">Quay lại thư viện</Link></p> : card && <div className="flashcard-columns"><aside className="flashcard-topics"><h2>DANH SÁCH TỪ VỰNG</h2>{data.cards.map((c,i) => <button key={c.id} type="button" className={current === i ? 'selected' : ''} onClick={() => { setCurrent(i); setRevealed(false); }}><span className="topic-emoji">📘</span><span><strong>{c.word}</strong><small>{c.partOfSpeech}</small></span><span className="topic-dot">{i+1}</span></button>)}</aside><section className="flashcard-study"><div className="flashcard-progress"><span>TIẾN ĐỘ — {data.deck?.category || 'TỪ VỰNG'}</span><span>{current} / {data.cards.length} THẺ</span></div><div className="thin-progress"><span style={{ width: `${current / data.cards.length * 100}%` }} /></div><div className={`flashcard surface ${revealed ? 'revealed' : ''}`}><AlertTriangle size={16} className="flashcard-alert" />
      <button type="button" className="flashcard-flip" aria-label={revealed ? 'Ẩn nghĩa' : 'Lật thẻ xem nghĩa'} onClick={() => setRevealed(!revealed)}>{card.image && <img className="flashcard-picture" src={assetUrl(card.image)} alt={card.word} />}<h2>{card.word}</h2><span className="word-type">{card.partOfSpeech}</span></button><div className="word-phonetics"><span>{card.phonetic}</span><button type="button" className="icon-button" onClick={() => speak(card.word)} aria-label="Nghe phát âm"><Volume2 size={16} /></button></div>
      {revealed && <div className="flashcard-definition"><h3>{card.definitionVi}</h3><h4>ĐỊNH NGHĨA</h4><p><b>VI:</b> {card.definitionVi}</p><h4>VÍ DỤ <button type="button" onClick={() => speak(card.exampleEn)} aria-label="Nghe câu ví dụ"><Volume2 size={14} /></button></h4><p className="example-en">{card.exampleEn}</p><p className="example-vi">{card.exampleVi}</p>{card.collocations && <p>{card.collocations}</p>}</div>}
      <button type="button" className="flip-hint" onClick={() => setRevealed(!revealed)}>Nhấn để lật thẻ</button></div><p className="flashcard-caption">Nhóm học {current+1}/{data.cards.length} • Tổng cộng {data.deck?.totalCards ?? data.cards.length} thẻ trong bộ thẻ</p>{revealed && <div className="rating-buttons">{ratings.map(([value,title,interval]) => <button key={value} type="button" disabled={saving} onClick={() => rate(value)} className={`rating-${value.toLowerCase()}`}><strong>{title}</strong><small>{interval}</small></button>)}</div>}
    </section></div>}
  </div></LearnerTemplate>;
}
