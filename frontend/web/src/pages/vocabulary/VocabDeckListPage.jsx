
import { useState, useEffect } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import {
  BookOpen,
  FolderPlus,
  Layers,
  Target,
  Clock,
  Tag,
} from 'lucide-react';

import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { DeckCover } from '@/components/molecules/DeckCover';
import { FigmaAsset } from '@/components/atoms/FigmaAsset';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { vocabularyRepo } from '@/features/vocabulary/repositories/vocabulary.repo';
import { reviewProgress, reviewAction, clearReviewSession } from '@/features/vocabulary/reviewProgress';
import DeckEditor from '@/components/organisms/DeckEditor';
import { useMock } from '@/lib/runtime-config';

export default function VocabDeckListPage() {
  const [decks, setDecks] = useState([]);
  const [deckProgress, setDeckProgress] = useState({});
  const [progressError, setProgressError] = useState('');
  const [catalogDecks, setCatalogDecks] = useState([]);
  const [catalogError, setCatalogError] = useState('');
  const [startingDeckId, setStartingDeckId] = useState(null);
  const [startError, setStartError] = useState('');
  const navigate = useNavigate();
  const [reviewStats, setReviewStats] = useState(null);
  const [dueItems, setDueItems] = useState([]);
  const [deckCardsById, setDeckCardsById] = useState({});
  const [showDueList, setShowDueList] = useState(false);
  const [dueError, setDueError] = useState('');
  const [dueActiveId, setDueActiveId] = useState(null);
  const [dueRevealed, setDueRevealed] = useState(false);
  const [dueSaving, setDueSaving] = useState(false);
  const [dueNotice, setDueNotice] = useState('');
  const [confirmRestart, setConfirmRestart] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [statsError, setStatsError] = useState('');
  const [tag, setTag] = useState('Tất cả');
  const [showReviewPicker, setShowReviewPicker] = useState(false);
  const [selectedReviewDeckId, setSelectedReviewDeckId] = useState('');
  const [reviewMode, setReviewMode] = useState('continue');
  const [showAllPersonalDecks, setShowAllPersonalDecks] = useState(false);
  const [showAllCatalogDecks, setShowAllCatalogDecks] = useState(false);

  const [search] = useSearchParams();

  useEffect(() => {
    let active = true;

    Promise.allSettled([
      vocabularyRepo.getDecks(),
      vocabularyRepo.getReviewStatistics(),
      vocabularyRepo.getCatalogDecks(),
      vocabularyRepo.getDueReviews(),
    ])
      .then(([deckResult, statsResult, catalogResult, dueResult]) => {
        if (!active) return;

        if (deckResult.status === 'fulfilled') {
          const personalDecks = Array.isArray(deckResult.value) ? deckResult.value : [];
          setDecks(personalDecks);
          Promise.allSettled(personalDecks.map((deck) => vocabularyRepo.getDeckCards(deck.id)))
            .then((results) => {
              if (!active) return;
              const progress = {};
              const cardIndex = {};
              let failed = 0;
              results.forEach((result, index) => {
                if (result.status === 'fulfilled') {
                  progress[personalDecks[index].id] = reviewProgress(personalDecks[index].id, result.value);
                  cardIndex[personalDecks[index].id] = result.value;
                }
                else failed += 1;
              });
              setDeckProgress(progress);
              setDeckCardsById(cardIndex);
              if (failed) setProgressError(`Không tải được tiến độ của ${failed} bộ thẻ.`);
            });
        } else {
          setError(
            deckResult.reason?.message ||
              'Không tải được danh sách bộ thẻ.'
          );
        }

        if (catalogResult.status === 'fulfilled') {
          setCatalogDecks(
            Array.isArray(catalogResult.value)
              ? catalogResult.value
              : []
          );
        } else {
          setCatalogError(
            catalogResult.reason?.message ||
              'Không tải được thư viện TOEIC.'
          );
        }

        if (dueResult.status === 'fulfilled') {
          setDueItems(Array.isArray(dueResult.value) ? dueResult.value : []);
        } else {
          setDueError('Chưa tải được danh sách từ cần ôn. Vui lòng thử lại sau.');
        }

        if (statsResult.status === 'fulfilled') {
          setReviewStats(statsResult.value);
        } else {
          setStatsError(
            statsResult.reason?.message ||
              'Không tải được thống kê SRS.'
          );
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  const query = (search.get('q') || '')
    .toLocaleLowerCase('vi');

  const filtered = catalogDecks.filter((deck) => {
    const text = `${deck.title ?? ''} ${deck.description ?? ''}`
      .toLocaleLowerCase('vi');

    const matchesQuery = text.includes(query);

    const matchesTag =
      tag === 'Tất cả' || deck.category === tag;

    return matchesQuery && matchesTag;
  });

  // Backend ReviewStatistics:
  // { totalVocabulary, dueVocabulary }
  const totalVocabulary =
    typeof reviewStats?.totalVocabulary === 'number'
      ? reviewStats.totalVocabulary
      : null;

  const dueVocabulary =
    typeof reviewStats?.dueVocabulary === 'number'
      ? reviewStats.dueVocabulary
      : null;

  const displayStat = (value) =>
    value === null ? '—' : value;

  const selectedReviewDeck = decks.find((deck) => deck.id === selectedReviewDeckId);
  const selectedReviewProgress = deckProgress[selectedReviewDeckId];

  const openReviewPicker = () => {
    setSelectedReviewDeckId((previous) => decks.some((deck) => deck.id === previous) ? previous : (decks[0]?.id || ''));
    setReviewMode('continue');
    setShowReviewPicker(true);
  };

  const launchReview = () => {
    if (!selectedReviewDeck || !selectedReviewProgress) return;
    if (reviewMode === 'restart') {
      const count = selectedReviewProgress.reviewed;
      if (count > 0) { setConfirmRestart(true); return; }
      clearReviewSession(selectedReviewDeck.id);
    }
    setShowReviewPicker(false);
    navigate(`/vocabulary/${selectedReviewDeck.id}/review`);
  };

  const confirmNewRound = () => {
    if (!selectedReviewDeck) return;
    clearReviewSession(selectedReviewDeck.id);
    setConfirmRestart(false);
    setShowReviewPicker(false);
    navigate(`/vocabulary/${selectedReviewDeck.id}/review`);
  };

  const dueDetails = dueItems.map((item) => {
    for (const deck of decks) {
      const card = (deckCardsById[deck.id] || []).find((entry) => entry.id === item.vocabularyId);
      if (card) return { ...item, card, deck };
    }
    return { ...item, card: null, deck: null };
  });

  const activeDue = dueDetails.find((item) => item.vocabularyId === dueActiveId) || null;

  const openDueReview = (id) => {
    setDueError('');
    setDueNotice('');
    setDueActiveId(id);
    setDueRevealed(false);
  };

  const submitDueRating = async (rating) => {
    if (!activeDue || dueSaving) return;
    setDueSaving(true);
    setDueError('');
    try {
      await vocabularyRepo.rateCard(activeDue.vocabularyId, rating);
      // Refresh from backend: only the server decides whether a word remains due.
      const [nextDue, nextStats] = await Promise.all([
        vocabularyRepo.getDueReviews(),
        vocabularyRepo.getReviewStatistics(),
      ]);
      setDueItems(Array.isArray(nextDue) ? nextDue : []);
      setReviewStats(nextStats);
      setDueActiveId(null);
      setDueRevealed(false);
      setDueNotice('Đã lưu đánh giá. Lịch ôn của bạn đã được cập nhật.');
    } catch (err) {
      setDueError(err?.message || 'Không lưu được đánh giá. Vui lòng thử lại.');
    } finally {
      setDueSaving(false);
    }
  };

  const categories = [
    'Tất cả',
    ...new Set(
      catalogDecks
        .map((deck) => deck.category)
        .filter(Boolean)
    ),
  ];

  const handleStartCatalogDeck = async (deckId) => {
    if (startingDeckId) return;

    setStartError('');
    setStartingDeckId(deckId);

    try {
      const result = await vocabularyRepo.startCatalogDeck(deckId);
      navigate(`/vocabulary/${result.personalDeckId}`);
    } catch (err) {
      setStartError(err?.message || 'Không thể bắt đầu học.');
    } finally {
      setStartingDeckId(null);
    }
  };

  return (
    <LearnerTemplate variant="catalog">
      <div className="vocabulary-library">
        {/* HEADER */}
        <div className="vocab-heading">
          <FigmaAsset
            screen="vocabulary-library"
            name="imgSvg17"
            alt="Parroto cầm sách"
          />

          <div>
            <h1>Học Từ Vựng Tiếng Anh</h1>
            <p>
              Thành thạo từ vựng tiếng Anh với hệ thống
              lặp lại ngắt quãng của Parroto
            </p>
          </div>

          <Link
            className="pill-button navy"
            to="/vocabulary?view=personal"
          >
            <BookOpen size={14} />
            Sổ tay từ vựng
          </Link>
        </div>

        {/* SRS STATISTICS */}
        <section className="vocab-statistics surface">
          <div>
            <h2>Thống Kê Học Tập</h2>

            {statsError && (
              <p className="form-error" role="alert">
                {statsError}
              </p>
            )}

            <div className="vocab-stat-grid">
              <div>
                <Target />
                <p>
                  Tổng số từ vựng
                  <strong>
                    {displayStat(totalVocabulary)}
                  </strong>
                </p>
              </div>

              <button type="button" className="flex items-center gap-3 text-left rounded-xl hover:bg-blue-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-500 p-1" onClick={() => setShowDueList(true)} aria-label="Xem những từ cần ôn tập">
                <Clock />
                <p>Đến hạn ôn tập<strong>{displayStat(dueVocabulary)}</strong><span className="block text-xs text-blue-600">Xem từ cần ôn</span></p>
              </button>
            </div>

            <div className="mt-6 flex w-full justify-center">
              <button
                type="button"
                className="vocab-race !w-auto min-w-[220px] max-w-full !px-7 !py-3 text-center"
                style={{ width: 'auto', maxWidth: '100%' }}
                onClick={openReviewPicker}
                disabled={loading || decks.length === 0}
              >
                ϟ Bắt Đầu Ôn Từ Vựng
              </button>
            </div>
          </div>

          <div>
            <h2>Tiến độ các bộ thẻ của tôi</h2>
            <p className="text-xs text-gray-500 mt-1 mb-3">Số thẻ đã đánh giá trong lượt học hiện tại (khác với mức độ thuộc theo SRS).</p>
            {progressError && <p className="form-error" role="alert">{progressError}</p>}
            {decks.length === 0 && <p className="empty-message">Chưa có bộ thẻ cá nhân.</p>}
            <div
              className="space-y-3 max-h-[220px] overflow-y-auto overscroll-contain pr-2"
              role="region"
              aria-label="Danh sách tiến độ các bộ thẻ, có thể cuộn"
              tabIndex={0}
            >
              {decks.map((deck) => {
                const progress = deckProgress[deck.id];
                return <div key={deck.id} className="rounded-lg border border-gray-100 p-3">
                  <div className="flex justify-between gap-3 text-sm font-semibold"><Link to={`/vocabulary/${deck.id}`}>{deck.title}</Link><span>{progress ? `${progress.reviewed}/${progress.total}` : 'Đang tải...'}</span></div>
                  {progress && <><div className="w-full h-2 bg-gray-100 rounded-full overflow-hidden mt-2" role="progressbar" aria-label={`Tiến độ ${deck.title}`} aria-valuenow={progress.reviewed} aria-valuemin={0} aria-valuemax={progress.total}><div className="h-full bg-blue-600 rounded-full" style={{ width: `${progress.percent}%` }} /></div><p className="text-xs text-gray-500 mt-1">{progress.percent}% · {progress.remaining} từ chưa đánh giá</p></>}
                </div>;
              })}
            </div>
          </div>
        </section>

        {showReviewPicker && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setShowReviewPicker(false); }}>
            <section role="dialog" aria-modal="true" aria-labelledby="review-picker-title" className="w-full max-w-xl max-h-[90vh] overflow-y-auto rounded-2xl bg-white p-5 sm:p-6 shadow-2xl text-slate-900">
              <div className="flex items-start justify-between gap-4 mb-4">
                <div><h2 id="review-picker-title" className="text-xl font-bold">Chọn bộ từ vựng để học</h2><p className="text-sm text-slate-500 mt-1">Chọn bộ thẻ cá nhân và hình thức học.</p></div>
                <button type="button" aria-label="Đóng cửa sổ" className="text-xl px-2" onClick={() => setShowReviewPicker(false)}>×</button>
              </div>
              <div className="space-y-2 max-h-[280px] overflow-y-auto overscroll-contain pr-2" role="radiogroup" aria-label="Chọn bộ thẻ" tabIndex={0}>
                {decks.map((deck) => {
                  const progress = deckProgress[deck.id];
                  return <label key={deck.id} className={`block cursor-pointer rounded-xl border p-3 ${selectedReviewDeckId === deck.id ? 'border-blue-600 bg-blue-50' : 'border-slate-200'}`}>
                    <span className="flex justify-between items-center gap-3"><span className="flex gap-2 items-center"><input type="radio" name="review-deck" value={deck.id} checked={selectedReviewDeckId === deck.id} onChange={() => setSelectedReviewDeckId(deck.id)} /><strong className="text-sm">{deck.title}</strong></span><span className="text-sm whitespace-nowrap">{progress ? `${progress.reviewed}/${progress.total}` : 'Đang tải...'}</span></span>
                    {progress && <><div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100"><div className="h-full bg-blue-600 rounded-full" style={{width:`${progress.percent}%`}} /></div><p className="mt-1 text-xs text-slate-500">{progress.remaining} từ chưa đánh giá trong lượt</p></>}
                  </label>;
                })}
              </div>
              <fieldset className="mt-5 space-y-2">
                <legend className="font-bold mb-2">Chế độ học</legend>
                <label className="flex items-start gap-3 rounded-xl border border-slate-200 p-3 cursor-pointer"><input className="mt-1" type="radio" name="review-mode" value="continue" checked={reviewMode === 'continue'} onChange={() => setReviewMode('continue')} /><span><strong>Tiếp tục học</strong><span className="block text-sm text-slate-500">{selectedReviewProgress?.completed ? 'Lượt hiện tại đã hoàn thành. Chọn học lại toàn bộ để bắt đầu lượt mới.' : `Tiếp tục ${selectedReviewProgress?.remaining ?? '...'} từ chưa đánh giá.`}</span></span></label>
                <label className="flex items-start gap-3 rounded-xl border border-slate-200 p-3 cursor-pointer"><input className="mt-1" type="radio" name="review-mode" value="restart" checked={reviewMode === 'restart'} onChange={() => setReviewMode('restart')} /><span><strong>Học lại toàn bộ</strong><span className="block text-sm text-slate-500">Đặt lại tiến độ lượt học, không xóa lịch sử và lịch ôn SRS.</span></span></label>
              </fieldset>
              <div className="flex justify-end gap-2 mt-5"><button type="button" className="rounded-xl border border-slate-300 px-4 py-2 font-semibold" onClick={() => setShowReviewPicker(false)}>Hủy</button><button type="button" className="rounded-xl bg-blue-600 px-5 py-2 text-white font-bold disabled:opacity-50" disabled={!selectedReviewProgress || selectedReviewProgress.total === 0 || (reviewMode === 'continue' && selectedReviewProgress.completed)} onClick={launchReview}>{reviewMode === 'restart' ? 'Bắt đầu lượt mới' : 'Tiếp tục học'}</button></div>
            </section>
          </div>
        )}

        {showDueList && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4" onMouseDown={(event) => { if (event.target === event.currentTarget && !dueSaving) { setShowDueList(false); setDueActiveId(null); } }}>
            <section role="dialog" aria-modal="true" aria-labelledby="due-list-title" className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-2xl text-slate-900">
              <div className="flex items-start justify-between gap-3">
                <div><h2 id="due-list-title" className="text-xl font-bold">{activeDue ? 'Ôn tập từ vựng' : 'Từ cần ôn hôm nay'}</h2><p className="mt-1 text-sm text-slate-500">{activeDue ? 'Nhớ lại nghĩa trước khi xem đáp án nhé.' : 'Các từ đã đến lịch ôn theo quá trình học của bạn.'}</p></div>
                <button type="button" disabled={dueSaving} aria-label="Đóng" className="rounded-lg px-2 py-1 text-xl hover:bg-slate-100" onClick={() => { setShowDueList(false); setDueActiveId(null); }}>×</button>
              </div>
              {dueError && <p className="mt-4 text-sm text-red-600" role="alert">{dueError}</p>}
              {dueNotice && !activeDue && <p className="mt-4 text-sm text-green-700" role="status">{dueNotice}</p>}
              {activeDue ? (
                <div className="mt-5 space-y-4">
                  <div className="rounded-xl border border-slate-200 bg-slate-50 p-6 text-center">
                    <p className="text-xs text-slate-500">{activeDue.deck?.title || 'Từ vựng của bạn'}</p>
                    <h3 className="mt-3 text-3xl font-bold">{activeDue.card?.word || 'Từ vựng'}</h3>
                    {activeDue.card?.partOfSpeech && <p className="mt-1 text-sm text-slate-500">{activeDue.card.partOfSpeech}</p>}
                    {dueRevealed ? <div className="mt-5 border-t border-slate-200 pt-4"><p className="font-semibold">{activeDue.card?.definitionVi || 'Không có nghĩa tiếng Việt trong dữ liệu hiện tại.'}</p>{activeDue.card?.exampleEn && <p className="mt-2 text-sm text-slate-600">{activeDue.card.exampleEn}</p>}</div> : <button type="button" className="mt-5 rounded-xl bg-blue-600 px-5 py-2 font-semibold text-white" onClick={() => setDueRevealed(true)}>Xem nghĩa</button>}
                  </div>
                  {dueRevealed && <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                    {[[1, 'HỌC LẠI'], [2, 'KHÓ'], [3, 'TỐT'], [4, 'DỄ']].map(([value, label]) => <button key={value} type="button" disabled={dueSaving} onClick={() => submitDueRating(value)} className="rounded-xl border border-slate-200 px-2 py-3 text-sm font-semibold hover:bg-blue-50 disabled:opacity-50">{label}</button>)}
                  </div>}
                  <button type="button" disabled={dueSaving} className="rounded-lg border border-slate-200 px-4 py-2 text-sm" onClick={() => { setDueActiveId(null); setDueRevealed(false); setDueError(''); }}>Quay lại danh sách</button>
                </div>
              ) : (
                <>
                  {!dueError && dueDetails.length === 0 && <p className="mt-5 text-slate-600">Bạn đã ôn xong các từ đến hạn. Hẹn gặp lại trong lượt ôn tiếp theo!</p>}
                  <div className="mt-4 max-h-[320px] space-y-2 overflow-y-auto overscroll-contain pr-1">
                    {dueDetails.map((item) => <div key={item.vocabularyId} className="rounded-xl border border-slate-200 p-3">
                      <div className="font-semibold">{item.card?.word || 'Từ vựng đã lưu'}</div>
                      {item.card?.definitionVi && <p className="text-sm text-slate-600">{item.card.definitionVi}</p>}
                      <p className="mt-1 text-xs text-slate-500">{item.deck?.title || 'Từ vựng cá nhân'}</p>
                      <div className="mt-2 flex flex-wrap gap-3"><button type="button" className="text-sm font-semibold text-blue-600" onClick={() => openDueReview(item.vocabularyId)}>Ôn ngay</button>{item.deck && <Link className="text-sm font-semibold text-slate-600" to={`/vocabulary/${item.deck.id}`} onClick={() => setShowDueList(false)}>Xem bộ từ</Link>}</div>
                    </div>)}
                  </div>
                  <div className="mt-5 flex justify-end"><button type="button" className="rounded-xl bg-blue-600 px-5 py-2 font-semibold text-white" onClick={() => setShowDueList(false)}>Đóng</button></div>
                </>
              )}
            </section>
          </div>
        )}

        {confirmRestart && selectedReviewDeck && (
          <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-950/60 p-4">
            <section role="alertdialog" aria-modal="true" aria-labelledby="restart-title" aria-describedby="restart-description" className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl text-slate-900">
              <h2 id="restart-title" className="text-xl font-bold">Bạn muốn học lại từ đầu?</h2>
              <p id="restart-description" className="mt-3 text-sm text-slate-600">Bạn sẽ bắt đầu một lượt học mới với tất cả từ trong bộ <strong>{selectedReviewDeck.title}</strong>. Những từ đã học và lịch ôn của bạn vẫn được giữ lại.</p>
              <div className="mt-6 flex flex-wrap justify-end gap-2"><button type="button" className="rounded-xl border border-slate-300 px-4 py-2" onClick={() => setConfirmRestart(false)}>Tiếp tục lượt cũ</button><button type="button" className="rounded-xl bg-blue-600 px-4 py-2 font-semibold text-white" onClick={confirmNewRound}>Học lại từ đầu</button></div>
            </section>
          </div>
        )}

        {!useMock && <DeckEditor />}

        {/* PERSONAL DECKS */}
        <section className="personal-decks surface">
          <div className="personal-decks-heading flex items-center gap-3">
            <BookOpen size={23} />
            <div className="flex-1 min-w-0">
              <h2>BỘ THẺ CỦA TÔI</h2>
              <p>Bộ thẻ cá nhân để bắt đầu quản lý từ vựng của bạn</p>
            </div>
            {decks.length > 4 && (
              <button type="button" aria-expanded={showAllPersonalDecks}
                className="shrink-0 rounded-lg border border-slate-200 px-3 py-2 text-sm font-semibold text-blue-600 hover:bg-blue-50"
                onClick={() => setShowAllPersonalDecks((previous) => !previous)}>
                {showAllPersonalDecks ? 'Thu gọn' : 'Xem tất cả'}
              </button>
            )}
          </div>

          {loading ? (
            <LoadingSpinner text="Đang tải bộ thẻ..." />
          ) : error ? (
            <p className="form-error" role="alert">
              {error}
            </p>
          ) : decks.length === 0 ? (
            <div className="personal-decks-empty">
              <FolderPlus size={48} />
              <h3>Chưa có bộ thẻ nào</h3>
              <p>
                Hãy tạo bộ thẻ để bắt đầu học từ vựng.
              </p>
              <a
                className="pill-button navy"
                href="#vocab-library"
              >
                Khám phá thư viện
              </a>
            </div>
          ) : (
            <div className="vocab-deck-grid">
              {(showAllPersonalDecks ? decks : decks.slice(0, 4)).map((deck, index) => (
                <article
                  className="vocab-deck surface"
                  key={deck.id}
                >
                  <Link to={`/vocabulary/${deck.id}`}>
                    <DeckCover
                      title={deck.title}
                      index={index}
                      category={deck.category}
                    />
                  </Link>

                  <div className="vocab-deck-body">
                    <Link to={`/vocabulary/${deck.id}`}>
                      <h3>{deck.title}</h3>
                    </Link>

                    <p>
                      {deck.description ||
                        'Bộ thẻ từ vựng cá nhân'}
                    </p>

                    {deckProgress[deck.id] && <div className="mt-3 mb-2">
                      <div className="flex justify-between text-xs text-gray-600"><span>Đã học trong lượt</span><strong>{deckProgress[deck.id].reviewed}/{deckProgress[deck.id].total} từ ({deckProgress[deck.id].percent}%)</strong></div>
                      <div className="h-2 w-full bg-gray-100 rounded-full overflow-hidden mt-1" role="progressbar" aria-label={`Tiến độ ${deck.title}`} aria-valuenow={deckProgress[deck.id].reviewed} aria-valuemin={0} aria-valuemax={deckProgress[deck.id].total}><div className="h-full bg-blue-600 rounded-full" style={{ width: `${deckProgress[deck.id].percent}%` }} /></div>
                    </div>}
                    <div className="flex gap-2 mt-2">
                      <Link
                        className="pill-button navy flex-1 text-center"
                        to={`/vocabulary/${deck.id}/review`}
                      >
                        {reviewAction(deckProgress[deck.id])}
                      </Link>

                      <Link
                        className="pill-button flex-1 text-center border border-gray-200"
                        to={`/vocabulary/${deck.id}`}
                      >
                        XEM TỪ
                      </Link>
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>

        {/* TAG FILTER */}
        <div className="vocab-tags">
          <p>
            <Tag size={14} />
            Lọc theo Tags
          </p>

          <div>
            {categories.map((category) => (
              <button
                key={category}
                type="button"
                className={`pill-button ${
                  tag === category ? 'navy' : ''
                }`}
                aria-pressed={tag === category}
                onClick={() => setTag(category)}
              >
                {category === 'Tất cả'
                  ? category
                  : `#${category}`}
              </button>
            ))}
          </div>
        </div>

        {/* DECK LIBRARY */}
        <section id="vocab-library">
          <div className="section-heading flex items-center justify-between gap-4">
            <h2>
              Từ Vựng TOEIC
              <small> ({filtered.length} bộ thẻ)</small>
            </h2>
            {filtered.length > 4 && (
              <button type="button" aria-expanded={showAllCatalogDecks}
                className="shrink-0 rounded-lg border border-slate-200 px-3 py-2 text-sm font-semibold text-blue-600 hover:bg-blue-50"
                onClick={() => setShowAllCatalogDecks((previous) => !previous)}>
                {showAllCatalogDecks ? 'Thu gọn' : 'Xem tất cả'}
              </button>
            )}
          </div>

          {loading && (
            <LoadingSpinner text="Đang tải thư viện TOEIC..." />
          )}

          {catalogError && (
            <p className="form-error" role="alert">
              {catalogError}
            </p>
          )}

          {startError && (
            <p className="form-error" role="alert">
              {startError}
            </p>
          )}

          {!loading && !catalogError && filtered.length === 0 && (
            <p className="empty-message">
              Không tìm thấy bộ từ vựng phù hợp.
            </p>
          )}

          {!loading && !catalogError && (
            <div className="vocab-deck-grid">
              {(showAllCatalogDecks ? filtered : filtered.slice(0, 4)).map((deck, index) => (
                <article className="vocab-deck surface" key={deck.id}>
                  <DeckCover
                    title={deck.title}
                    index={index}
                    category={deck.category}
                  />

                  <div className="vocab-deck-body">
                    <h3>{deck.title}</h3>

                    <p>
                      <Layers size={12} />
                      {deck.description || 'Bộ từ vựng TOEIC'}
                    </p>

                    <button
                      type="button"
                      className="pill-button navy w-full"
                      disabled={Boolean(startingDeckId)}
                      onClick={() => handleStartCatalogDeck(deck.id)}
                    >
                      {startingDeckId === deck.id
                        ? 'ĐANG CHUẨN BỊ...'
                        : 'BẮT ĐẦU HỌC'}
                    </button>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
        </div>
    </LearnerTemplate>
  );
}
