import { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { Volume2, ChevronLeft, CheckCircle2, AlertTriangle } from 'lucide-react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { assetUrl } from '@/lib/figma-assets';
import { vocabularyRepo } from '@/features/vocabulary/repositories/vocabulary.repo';
import './VocabReviewPage.layout.css';
import { useMock } from '@/lib/runtime-config';
import { readReviewSession, saveReviewSession, clearReviewSession } from '@/features/vocabulary/reviewProgress';
import { restoreLearningSession, readyLearningQueue, applyLearningRating } from '@/features/vocabulary/learningQueue';

const ratings = [
  [1, 'HỌC LẠI', '10 phút'],
  [2, 'KHÓ', '1 ngày'],
  [3, 'TỐT', 'Theo lịch SRS'],
  [4, 'DỄ', 'Theo lịch SRS'],
];

export default function VocabReviewPage() {
  const { deckId } = useParams();
  return <ReviewRound key={deckId} deckId={deckId} />;
}

async function loadRound(deckId) {
  const [cards, deck, remote] = await Promise.all([
    vocabularyRepo.getDeckCards(deckId),
    vocabularyRepo.getDeckDetail(deckId),
    useMock ? null : vocabularyRepo.getLearningSession(deckId),
  ]);
  const snapshot = useMock ? readReviewSession(deckId, cards) : remote?.data ?? {};
  return { cards, deck, snapshot, revision: remote?.revision ?? 0 };
}

function ReviewRound({ deckId }) {
  const navigate = useNavigate();
  const { state } = useLocation();
  const [exitOpen, setExitOpen] = useState(false);
  const [exitError, setExitError] = useState('');
  const [data, setData] = useState(null);
  const [session, setSession] = useState(() => restoreLearningSession([]));
  const [revision, setRevision] = useState(0);
  const [pendingRating, setPendingRating] = useState(null);
  const [needsRefresh, setNeedsRefresh] = useState(false);
  const [clock, setClock] = useState(Date.now);
  const [revealed, setRevealed] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(state?.notice ?? '');
  const [lastSchedule, setLastSchedule] = useState(null);

  useEffect(() => {
    const timer = setInterval(() => setClock(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    let active = true;
    loadRound(deckId).then(({ cards, deck, snapshot, revision }) => {
      if (!active) return;
      setData({ cards, deck });
      setSession(restoreLearningSession(cards, snapshot));
      setRevision(revision);
    }).catch((err) => { if (active) setError(err.message || 'Không tải được bộ thẻ.'); });
    return () => { active = false; };
  }, [deckId]);

  const { reviewedIds, completedIds, deferredUntil, roundIds, roundNumber, attempts, currentId } = session;
  const completedSet = useMemo(() => new Set(completedIds), [completedIds]);
  const readyIds = readyLearningQueue(roundIds, deferredUntil, clock);
  const card = data?.cards.find((item) => item.id === currentId && readyIds.includes(item.id))
    ?? data?.cards.find((item) => item.id === readyIds[0]);
  const progress = data?.cards.length ? Math.round(completedIds.length / data.cards.length * 100) : 0;
  const pendingCount = data ? data.cards.length - completedIds.length : 0;
  const completed = data?.cards.length > 0 && pendingCount === 0;
  const blocked = saving || needsRefresh || Boolean(pendingRating);

  function showSnapshot(snapshot, cards = data.cards) {
    setSession(restoreLearningSession(cards, snapshot?.data ?? {}));
    setRevision(snapshot?.revision ?? 0);
    setClock(Date.now());
    setRevealed(false);
  }

  async function refreshRound() {
    setSaving(true);
    setNeedsRefresh(true);
    try {
      const fresh = await loadRound(deckId);
      setData({ cards: fresh.cards, deck: fresh.deck });
      showSnapshot({ data: fresh.snapshot, revision: fresh.revision }, fresh.cards);
      setNeedsRefresh(false);
      setLastSchedule(null);
      setError('Đã tải tiến độ mới nhất. Hãy kiểm tra thẻ hiện tại trước khi tiếp tục.');
    } catch {
      setError('Chưa tải lại được tiến độ từ máy chủ.');
    } finally {
      setSaving(false);
    }
  }

  async function submitRating(request) {
    if (saving || needsRefresh) return;
    setSaving(true);
    setError('');
    try {
      if (useMock) {
        const result = await vocabularyRepo.rateCard(request.vocabularyId, request.rating);
        const next = applyLearningRating(data.cards, completedIds, roundIds, request.vocabularyId,
          request.rating, result, deferredUntil);
        const snapshot = {
          reviewedIds: [...new Set([...reviewedIds, request.vocabularyId])],
          completedIds: next.completedIds, deferredUntil: next.deferredUntil, roundIds: next.queue,
          roundNumber: roundNumber + (result.intervalDays === 0 && next.queue.length === 1 ? 1 : 0),
          attempts: attempts + 1, currentId: next.queue[0] ?? null,
        };
        if (!saveReviewSession(deckId, snapshot)) {
          setError('Trình duyệt chưa lưu được tiến độ.');
        }
        showSnapshot({ data: snapshot });
        setLastSchedule(result.nextReviewAt);
      } else {
        const result = await vocabularyRepo.rateLearningSession(deckId, request);
        showSnapshot(result.session);
        setLastSchedule(result.review.nextReviewAt);
      }
      setPendingRating(null);
    } catch (err) {
      if (!useMock && (err.status === 409 || err.status === 404)) {
        setPendingRating(null);
        await refreshRound();
      } else if (!useMock && (!err.status || err.status >= 500)) {
        setPendingRating(request);
        setError('Chưa xác nhận được kết quả đánh giá. Bạn có thể gửi lại cùng yêu cầu.');
      } else {
        setPendingRating(null);
        setError(err.message || 'Chưa lưu được đánh giá.');
      }
    } finally {
      setSaving(false);
    }
  }

  function rate(value) {
    if (blocked || !card) return;
    void submitRating({ vocabularyId: card.id, rating: value, expectedRevision: revision });
  }

  async function changeRound(save) {
    const snapshot = { ...session, currentId: card?.id ?? null };
    if (!useMock) {
      const result = save
        ? await vocabularyRepo.saveLearningSession(deckId, revision, snapshot)
        : await vocabularyRepo.clearLearningSession(deckId, revision);
      showSnapshot(result);
    } else if (save) {
      if (!saveReviewSession(deckId, snapshot)) throw new Error('Không thể lưu tiến độ trên trình duyệt.');
    } else {
      clearReviewSession(deckId);
      showSnapshot(null);
    }
  }

  async function restart() {
    if (blocked || !data) return;
    setSaving(true);
    setError('');
    try {
      await changeRound(false);
      setLastSchedule(null);
    } catch (err) {
      if (err.status === 409 || err.status === 404) await refreshRound();
      else setError(err.message || 'Chưa thể bắt đầu lượt mới.');
    } finally {
      setSaving(false);
    }
  }

  async function exit(save) {
    if (blocked || !data) return;
    setExitError('');
    setSaving(true);
    try {
      await changeRound(save);
      navigate('/vocabulary');
    } catch (err) {
      if (err.status === 409 || err.status === 404) {
        setExitOpen(false);
        await refreshRound();
      } else {
        setExitError(err.message || 'Chưa lưu được phiên học.');
      }
    } finally {
      setSaving(false);
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
    <button type="button" className="breadcrumb-back review-back-button" onClick={() => setExitOpen(true)} disabled={blocked || !data}><ChevronLeft size={13} />Quay lại thư viện</button>
    <div className="flashcard-title"><h1>{data?.deck?.title || 'Ôn tập từ vựng'}</h1><span className="pill-button navy">Flashcard</span></div>
    {error && <p className="form-error" role="alert">{error}</p>}
    {pendingRating && <button type="button" className="pill-button navy" disabled={saving}
      onClick={() => submitRating(pendingRating)}>Thử gửi lại đánh giá</button>}
    {needsRefresh && <button type="button" className="pill-button navy" disabled={saving}
      onClick={refreshRound}>Tải lại tiến độ</button>}
    {!data && !error && <LoadingSpinner text="Đang tải bộ thẻ..." />}
    {completed ? <div className="review-complete surface">
      <CheckCircle2 size={56} /><h2>Bạn đã hoàn thành lượt học!</h2>
      <p>{completedIds.length}/{data.cards.length} từ đã được hẹn ôn từ 1 ngày trở lên. Hoàn thành lượt học không đồng nghĩa đã thành thạo từ.</p>
      {lastSchedule && <p>Lịch ôn vừa cập nhật: {new Date(lastSchedule).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' })}</p>}

      <button type="button" className="pill-button navy" onClick={restart} disabled={blocked}>Học lượt mới</button>
      <button type="button" className="pill-button navy" onClick={() => navigate('/vocabulary')}>Quay lại thư viện</button>
    </div> : data && !card ? <div className="surface p-6" role="status">{data.cards.length ? <><h2>Đang chờ đến lịch học lại</h2><p>Các từ còn lại chưa đến hạn. Từ sẽ tự xuất hiện khi đến lịch ôn.</p><p>Lịch gần nhất: {new Date(Math.min(...roundIds.map(id => Date.parse(deferredUntil[id])).filter(Number.isFinite))).toLocaleString('vi-VN')}</p></> : <p>Bộ thẻ hiện chưa có từ vựng.</p>}<button className="pill-button navy" disabled={blocked} onClick={() => setExitOpen(true)}>Lưu tiến độ và thoát</button></div>
    : card && <div className="flashcard-columns review-columns">
      <aside className="flashcard-topics"><h2>DANH SÁCH TỪ VỰNG</h2>
        {data.cards.map((item, i) => <button key={item.id} type="button"
          disabled={blocked || !readyIds.includes(item.id)}
          className={card.id === item.id ? 'selected' : ''}
          onClick={() => {
            const selected = { ...session, currentId: item.id };
            setSession(selected);
            setRevealed(false);
            if (useMock) saveReviewSession(deckId, selected);
          }}>
          <span className="topic-emoji">{completedSet.has(item.id) ? '✓' : '📘'}</span>
          <span><strong>{item.word}</strong><small>{item.partOfSpeech}</small></span>
          <span className="topic-dot">{i + 1}</span>
        </button>)}
      </aside>
      <section className="flashcard-study review-study">
        <div className="flashcard-progress"><span>TIẾN ĐỘ — {data.deck?.category || 'TỪ VỰNG'}</span><span>{`ĐÃ HOÀN THÀNH ${completedIds.length}/${data.cards.length} · ${progress}%`}</span></div>
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
        <p className="flashcard-caption">{`Đã đánh giá ${reviewedIds.length}/${data.cards.length} · Hoàn thành ${completedIds.length} · Còn lại ${pendingCount} · ${attempts} lượt đánh giá`}</p>
        <p className="review-learning-hint">KHÓ, TỐT và DỄ hoàn thành từ trong lượt này khi lịch ôn tiếp từ 1 ngày trở lên. HỌC LẠI chờ đến hạn, không lặp ngay.</p>
        <div className="review-actions-area">
          <div className="rating-buttons review-rating-buttons">{ratings.map(([value, title, interval]) => <button key={value} type="button" disabled={blocked}
          onClick={() => rate(value)} className={`rating-${['again', 'hard', 'good', 'easy'][value - 1]}`}>
          <strong>{title}</strong><small>{interval}</small>
        </button>)}</div>
          <div className="review-exit-row"><button type="button" disabled={blocked} className="pill-button navy review-exit-button" onClick={() => setExitOpen(true)}>Thoát</button></div>
        </div>
      </section>
    </div>}
    {exitOpen && <div className="review-modal-backdrop" role="presentation" onMouseDown={(event) => { if (!saving && event.target === event.currentTarget) setExitOpen(false); }}>
      <div className="review-modal" role="dialog" aria-modal="true" aria-labelledby="review-exit-title">
        <h2 id="review-exit-title">Bạn muốn lưu tiến độ học không?</h2>
        <p>Bạn đã hoàn thành {completedIds.length}/{data?.cards.length ?? 0} từ trong lượt này. Tiến độ và thời điểm chờ học lại có thể được lưu để tiếp tục lần sau.</p>
        <p className="review-modal-note">Các đánh giá đã lưu vào lịch ôn vẫn được giữ nguyên, kể cả khi bạn không lưu lượt học.</p>
        {exitError && <p role="alert" style={{ color: "#b42318", fontSize: 14, marginTop: 10 }}>{exitError}</p>}
        <div className="review-modal-actions">
          <button type="button" disabled={blocked} onClick={() => { setExitError(''); setExitOpen(false); }}>Tiếp tục học</button>
          <button type="button" disabled={blocked} onClick={() => exit(false)}>{saving ? 'Đang xử lý...' : 'Không lưu'}</button>
          <button type="button" disabled={blocked} className="review-modal-primary" onClick={() => exit(true)}>{saving ? 'Đang xử lý...' : 'Lưu và thoát'}</button>
        </div>
      </div>
    </div>}
  </div></LearnerTemplate>;
}
