import { useState, useEffect, useMemo } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { Volume2, ChevronLeft, CheckCircle2, AlertTriangle } from 'lucide-react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { assetUrl } from '@/lib/figma-assets';
import { vocabularyRepo } from '@/features/vocabulary/repositories/vocabulary.repo';
import './VocabReviewPage.layout.css';
import { readReviewSession, saveReviewSession, clearReviewSession } from '@/features/vocabulary/reviewProgress';
import { restoreLearningQueue, applyLearningRating } from '@/features/vocabulary/learningQueue';
import { useMock } from '@/lib/runtime-config';

const ratings = [
  [1, 'HỌC LẠI', '10 phút'],
  [2, 'KHÓ', '1 ngày'],
  [3, 'TỐT', 'Theo lịch SRS'],
  [4, 'DỄ', 'Theo lịch SRS'],
];

export default function VocabReviewPage() {
  const { deckId } = useParams();
  const navigate = useNavigate();
  const [exitOpen, setExitOpen] = useState(false);
  const [exitError, setExitError] = useState('');
  const [exiting, setExiting] = useState(false);
  const [easyIds, setEasyIds] = useState([]);
  const [roundIds, setRoundIds] = useState([]);
  const [roundNumber, setRoundNumber] = useState(1);
  const [attempts, setAttempts] = useState(0);
  const [data, setData] = useState(null);
  const [currentId, setCurrentId] = useState(null);
  const [reviewedIds, setReviewedIds] = useState([]);
  const [revealed, setRevealed] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [completed, setCompleted] = useState(false);
  const [lastSchedule, setLastSchedule] = useState(null);

  useEffect(() => {
    let active = true;
    setData(null); setError(''); setCompleted(false); setReviewedIds([]); setCurrentId(null); setEasyIds([]); setRoundIds([]); setRoundNumber(1); setAttempts(0); setExitOpen(false);
    Promise.all([vocabularyRepo.getDeckCards(deckId), vocabularyRepo.getDecks(),
      vocabularyRepo.getLearningSession ? vocabularyRepo.getLearningSession(deckId) : Promise.resolve(null)])
      .then(([cards, decks, remote]) => {
        if (!active) return;
        const ids = new Set(cards.map((card) => card.id));
        const previous = remote?.data ?? (useMock ? readReviewSession(deckId, cards) : {});
        const reviewed = Array.isArray(previous?.reviewedIds)
          ? [...new Set(previous.reviewedIds.filter((id) => ids.has(id)))] : [];
        const easy = (previous.easyIds || []).filter((id) => ids.has(id));
        // Always recover every unfinished word, including legacy sessions.
        const initialRound = restoreLearningQueue(cards, easy, previous.roundIds);
        const nextId = initialRound.includes(previous.currentId) ? previous.currentId : initialRound[0] ?? null;
        setData({ cards, deck: decks.find((deck) => deck.id === deckId) });
        setReviewedIds(reviewed);
        setEasyIds(easy);
        setRoundIds(initialRound);
        setRoundNumber(previous.roundNumber || (reviewed.length === cards.length && easy.length < cards.length ? 2 : 1));
        setAttempts(previous.attempts || 0);
        setCurrentId(nextId);
        setCompleted(cards.length > 0 && easy.length === cards.length);
      })
      .catch((err) => { if (active) setError(err.message || 'Không tải được bộ thẻ.'); });
    return () => { active = false; };
  }, [deckId]);

  const reviewedSet = useMemo(() => new Set(reviewedIds), [reviewedIds]);
  const easySet = useMemo(() => new Set(easyIds), [easyIds]);
  const card = data?.cards.find((item) => item.id === currentId && roundIds.includes(item.id))
    ?? data?.cards.find((item) => item.id === roundIds[0]);
  const currentIndex = data?.cards.findIndex((item) => item.id === card?.id) ?? -1;
  const progress = data?.cards.length ? Math.round(easyIds.length / data.cards.length * 100) : 0;
  const roundPosition = Math.max(0, roundIds.indexOf(card?.id)) + 1;
  const pendingCount = data ? data.cards.length - easyIds.length : 0;

  async function rate(value) {
    if (saving || !card) return;
    setSaving(true); setError('');
    try {
      // Every rating is recorded by the existing backend SRS service.
      const result = await vocabularyRepo.rateCard(card.id, value);
      const updatedReviewed = reviewedSet.has(card.id) ? reviewedIds : [...reviewedIds, card.id];
      const next = applyLearningRating(data.cards, easyIds, roundIds, card.id, value);
      const updatedEasy = next.easyIds;
      const nextIds = next.queue;
      const allEasy = next.completed;
      const nextId = nextIds[0] ?? null;
      const nextRoundNumber = roundNumber + (value !== 4 && nextIds.length === 1 ? 1 : 0);
      const nextAttempts = attempts + 1;
      const saved = saveReviewSession(deckId, {
        reviewedIds: updatedReviewed, easyIds: updatedEasy,
        roundIds: nextIds, roundNumber: nextRoundNumber, currentId: nextId,
        attempts: nextAttempts,
      });
      if (vocabularyRepo.saveLearningSession) {
        try {
          await vocabularyRepo.saveLearningSession(deckId, {
            reviewedIds: updatedReviewed, easyIds: updatedEasy,
            roundIds: nextIds, roundNumber: nextRoundNumber,
            currentId: nextId, attempts: nextAttempts,
          });
        } catch {
          setError('Đã lưu đánh giá, nhưng chưa đồng bộ được tiến độ lên tài khoản. Tiến độ vẫn được giữ trên thiết bị này.');
        }
      } else if (!saved) setError('Đánh giá đã lưu, nhưng trình duyệt không lưu được tiến độ.');
      setReviewedIds(updatedReviewed);
      setEasyIds(updatedEasy);
      setRoundIds(nextIds);
      setRoundNumber(nextRoundNumber);
      setAttempts(nextAttempts);
      setCurrentId(nextId);
      setRevealed(false);
      setLastSchedule(result?.nextReviewAt ?? null);
      setCompleted(allEasy);
    } catch (err) {
      setError(err.message || 'Chưa lưu được đánh giá. Hãy thử lại.');
    } finally { setSaving(false); }
  }

  async function restart() {
    if (saving || !data) return;
    if (vocabularyRepo.clearLearningSession) {
      try { await vocabularyRepo.clearLearningSession(deckId); }
      catch { setError('Chưa thể bắt đầu lượt mới vì không xóa được tiến độ trên tài khoản.'); return; }
    }
    clearReviewSession(deckId);
    setReviewedIds([]);
    setEasyIds([]);
    setRoundIds(data.cards.map((item) => item.id));
    setRoundNumber(1);
    setAttempts(0);
    setCurrentId(data.cards[0]?.id ?? null);
    setCompleted(false);
    setRevealed(false);
    setLastSchedule(null);
  }

  async function exit(save) {
    if (saving || exiting) return;
    setExitError('');
    setExiting(true);
    try {
      if (save) {
        const snapshot = { reviewedIds, easyIds, roundIds, roundNumber, attempts, currentId: card?.id ?? null };
        const localSaved = saveReviewSession(deckId, snapshot);
        if (vocabularyRepo.saveLearningSession) {
          try {
            await vocabularyRepo.saveLearningSession(deckId, snapshot);
          } catch (err) {
            setExitError(`Không đồng bộ được tiến độ lên tài khoản: ${err?.message || 'Lỗi kết nối'}. ${localSaved ? 'Tiến độ đã được lưu trên trình duyệt. Bạn có thể thử lại.' : 'Không thể lưu tiến độ trên trình duyệt.'}`);
            return;
          }
        } else if (!localSaved) {
          setExitError('Không thể lưu tiến độ trên trình duyệt. Vui lòng thử lại.');
          return;
        }
      } else {
        if (vocabularyRepo.clearLearningSession) {
          try {
            await vocabularyRepo.clearLearningSession(deckId);
          } catch (err) {
            setExitError(`Không thể xóa phiên học trên tài khoản: ${err?.message || 'Lỗi kết nối'}. Hãy kiểm tra Backend và thử lại.`);
            return;
          }
        }
        clearReviewSession(deckId);
      }
      setExitOpen(false);
      navigate('/vocabulary');
    } finally {
      setExiting(false);
    }
  }

  function speak(text) {
    if (!text || !('speechSynthesis' in window)) return;
    window.speechSynthesis.cancel();
    const speech = new SpeechSynthesisUtterance(text);
    speech.lang = 'en-US'; speech.rate = 0.9;
    window.speechSynthesis.speak(speech);
  }

  return <LearnerTemplate variant="flashcard"><div className="flashcard-page">
    <button type="button" className="breadcrumb-back review-back-button" onClick={() => setExitOpen(true)} disabled={saving}><ChevronLeft size={13} />Quay lại thư viện</button>
    <div className="flashcard-title"><h1>{data?.deck?.title || 'Ôn tập từ vựng'}</h1><span className="pill-button navy">Flashcard</span></div>
    {error && <p className="form-error" role="alert">{error}</p>}
    {!data && !error && <LoadingSpinner text="Đang tải bộ thẻ..." />}
    {completed ? <div className="review-complete surface">
      <CheckCircle2 size={56} /><h2>Bạn đã ghi nhớ toàn bộ từ vựng!</h2>
      <p>Bạn đã chọn DỄ cho {easyIds.length}/{data.cards.length} từ. Lịch ôn SRS vẫn được cập nhật sau mỗi lần đánh giá.</p>
      {lastSchedule && <p>Lịch ôn vừa cập nhật: {new Date(lastSchedule).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' })}</p>}

      <button type="button" className="pill-button navy" onClick={restart}>Học lượt mới</button>
      <button type="button" className="pill-button navy" onClick={() => navigate('/vocabulary')}>Quay lại thư viện</button>
    </div> : data && !card ? <p className="empty-message">Bộ thẻ hiện chưa có từ vựng. <Link to="/vocabulary">Quay lại thư viện</Link></p>
    : card && <div className="flashcard-columns review-columns">
      <aside className="flashcard-topics"><h2>DANH SÁCH TỪ VỰNG</h2>
        {data.cards.map((item, i) => <button key={item.id} type="button"
          disabled={saving || !roundIds.includes(item.id)}
          className={card.id === item.id ? 'selected' : ''}
          onClick={() => { setCurrentId(item.id); setRevealed(false); saveReviewSession(deckId, { reviewedIds, easyIds, roundIds, roundNumber, attempts, currentId: item.id }); }}>
          <span className="topic-emoji">{easySet.has(item.id) ? '✓' : '📘'}</span>
          <span><strong>{item.word}</strong><small>{item.partOfSpeech}</small></span>
          <span className="topic-dot">{i + 1}</span>
        </button>)}
      </aside>
      <section className="flashcard-study review-study">
        <div className="flashcard-progress"><span>TIẾN ĐỘ — {data.deck?.category || 'TỪ VỰNG'}</span><span>{`ĐÃ ĐẠT DỄ ${easyIds.length}/${data.cards.length} · ${progress}%`}</span></div>
        <div className="thin-progress"><span style={{ width: `${progress}%` }} /></div>
        <div className={`flashcard surface ${revealed ? 'revealed' : ''}`}>
          <AlertTriangle size={16} className="flashcard-alert" />
          <button type="button" className="flashcard-flip" aria-label={revealed ? 'Ẩn nghĩa' : 'Lật thẻ xem nghĩa'} onClick={() => setRevealed(!revealed)}>
            {card.image && <img className="flashcard-picture" src={assetUrl(card.image)} alt={card.word} />}
            <h2>{card.word}</h2><span className="word-type">{card.partOfSpeech}</span>
          </button>
          <div className="word-phonetics"><span>{card.phonetic}</span><button type="button" className="icon-button" onClick={() => speak(card.word)} aria-label="Nghe phát âm"><Volume2 size={16} /></button></div>
          {revealed && <div className="flashcard-definition"><h3>{card.definitionVi}</h3><h4>ĐỊNH NGHĨA</h4><p><b>VI:</b> {card.definitionVi}</p>
            <h4>VÍ DỤ <button type="button" onClick={() => speak(card.exampleEn)} aria-label="Nghe câu ví dụ"><Volume2 size={14} /></button></h4>
            <p className="example-en">{card.exampleEn}</p><p className="example-vi">{card.exampleVi}</p>{card.collocations && <p>{card.collocations}</p>}
          </div>}
          <button type="button" className="flip-hint" onClick={() => setRevealed(!revealed)}>Nhấn để lật thẻ</button>
        </div>
        <p className="flashcard-caption">{`Đã xem ${reviewedIds.length}/${data.cards.length} · Đạt DỄ ${easyIds.length} · Cần luyện thêm ${pendingCount} · ${attempts} lượt đánh giá`}</p>
        <p className="review-learning-hint">HỌC LẠI và KHÓ sẽ được nhắc lại trong buổi học. Chỉ chọn DỄ khi bạn đã nhớ chắc từ này.</p>
        <div className="review-actions-area">
          <div className="rating-buttons review-rating-buttons">{ratings.map(([value, title, interval]) => <button key={value} type="button" disabled={saving}
          onClick={() => rate(value)} className={`rating-${['again', 'hard', 'good', 'easy'][value - 1]}`}>
          <strong>{title}</strong><small>{interval}</small>
        </button>)}</div>
          <div className="review-exit-row"><button type="button" disabled={saving} className="pill-button navy review-exit-button" onClick={() => setExitOpen(true)}>Thoát</button></div>
        </div>
      </section>
    </div>}
    {exitOpen && <div className="review-modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setExitOpen(false); }}>
      <div className="review-modal" role="dialog" aria-modal="true" aria-labelledby="review-exit-title">
        <h2 id="review-exit-title">Bạn muốn lưu tiến độ học không?</h2>
        <p>Bạn đã đạt DỄ {easyIds.length}/{data?.cards.length ?? 0} từ và đang ở vòng {roundNumber}. Bạn có thể tiếp tục lượt học này vào lần sau.</p>
        <p className="review-modal-note">Các đánh giá đã lưu vào lịch ôn vẫn được giữ nguyên, kể cả khi bạn không lưu lượt học.</p>
        {exitError && <p role="alert" style={{ color: "#b42318", fontSize: 14, marginTop: 10 }}>{exitError}</p>}
        <div className="review-modal-actions">
          <button type="button" disabled={exiting} onClick={() => { setExitError(''); setExitOpen(false); }}>Tiếp tục học</button>
          <button type="button" disabled={exiting} onClick={() => exit(false)}>{exiting ? 'Đang xử lý...' : 'Không lưu'}</button>
          <button type="button" disabled={exiting} className="review-modal-primary" onClick={() => exit(true)}>{exiting ? 'Đang xử lý...' : 'Lưu và thoát'}</button>
        </div>
      </div>
    </div>}
  </div></LearnerTemplate>;
}
