
import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  ChevronLeft,
  Layers,
  Volume2,
  Sparkles,
  BookOpen,
  CheckCircle,
  Clock,
} from 'lucide-react';

import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { vocabularyRepo } from '@/features/vocabulary/repositories/vocabulary.repo';
import { reviewProgress, reviewAction } from '@/features/vocabulary/reviewProgress';

function statusFor(state, now) {
  if (!state) return { label: 'Chưa học', style: 'bg-gray-100 text-gray-600' };
  if (!state.nextReviewAt || new Date(state.nextReviewAt).getTime() <= now) {
    return { label: 'Đến hạn ôn', style: 'bg-amber-100 text-amber-800' };
  }
  if (state.repetitionCount >= 2) {
    return { label: 'Đang ghi nhớ tốt', style: 'bg-green-100 text-green-800' };
  }
  return { label: 'Đang học', style: 'bg-blue-100 text-blue-800' };
}


export default function VocabDeckDetailPage() {
  const { deckId } = useParams();

  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [states, setStates] = useState({});
  const [sessionProgress, setSessionProgress] = useState(null);
  const [stateWarning, setStateWarning] = useState('');
  const [statesLoading, setStatesLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setStatesLoading(true);
    setStates({});
    setStateWarning('');

    Promise.all([
      vocabularyRepo.getDeckDetail(deckId),
      vocabularyRepo.getDeckCards(deckId),
    ])
      .then(([deck, cards]) => {
        if (!active) return;

        if (!deck) {
          throw new Error('Không tìm thấy bộ từ vựng.');
        }

        const words = Array.isArray(cards) ? cards : [];

        setSessionProgress(reviewProgress(deckId, words));
        setData({
          deck,
          groups: words.length
            ? [
                {
                  id: 'deck-words',
                  title: 'Danh sách từ vựng',
                  description: deck.description || '',
                  words,
                },
              ]
            : [],
          totalWords: words.length,
        });
        // A missing review state is expected for a vocabulary never reviewed (404).
        // Other failures are surfaced rather than being mistaken for "not learned".
        Promise.allSettled(words.map((word) => vocabularyRepo.getReviewState(word.id)))
          .then((results) => {
            if (!active) return;
            const next = {};
            let failures = 0;
            results.forEach((result, index) => {
              if (result.status === 'fulfilled') next[words[index].id] = result.value;
              else if (result.reason?.status !== 404) failures += 1;
            });
            setStates(next);
            setStateWarning(failures ? `Không tải được trạng thái của ${failures} từ. Hãy thử tải lại trang.` : '');
            setStatesLoading(false);
          });
      })
      .catch((err) => {
        if (active) {
          setStatesLoading(false);
          setError(
            err.message || 'Không thể tải chi tiết bộ từ vựng.'
          );
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [deckId]);

  const speak = (text) => {
    if (!text || !('speechSynthesis' in window)) return;

    window.speechSynthesis.cancel();

    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'en-US';
    utterance.rate = 0.9;

    window.speechSynthesis.speak(utterance);
  };

  if (loading) {
    return (
      <LearnerTemplate variant="catalog">
        <div className="py-12">
          <LoadingSpinner text="Đang tải danh sách từ vựng..." />
        </div>
      </LearnerTemplate>
    );
  }

  if (error || !data) {
    return (
      <LearnerTemplate variant="catalog">
        <div className="max-w-md mx-auto py-12 px-4 text-center">
          <p className="text-red-500 mb-4">
            {error || 'Không tìm thấy bộ từ vựng.'}
          </p>

          <Link
            to="/vocabulary"
            className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-semibold"
          >
            Quay lại thư viện
          </Link>
        </div>
      </LearnerTemplate>
    );
  }

  const { deck, groups, totalWords } = data;
  const now = Date.now();
  const stateValues = Object.values(states);
  const learnedCount = stateValues.filter((state) => state.repetitionCount >= 2 && new Date(state.nextReviewAt).getTime() > now).length;
  const dueCount = stateValues.filter((state) => new Date(state.nextReviewAt).getTime() <= now).length;
  const sessionCount = sessionProgress?.reviewed ?? 0;
  const actionLabel = reviewAction(sessionProgress);

  return (
    <LearnerTemplate variant="catalog">
      <div className="vocab-deck-detail max-w-3xl mx-auto px-4 py-4 pb-24 md:pb-12">
        {/* Navigation */}
        <Link
          to="/vocabulary"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-gray-500 hover:text-blue-600 transition-colors mb-4"
        >
          <ChevronLeft size={16} />
          Quay lại danh sách bộ từ vựng
        </Link>

        {/* Deck Header */}
        <div className="bg-white rounded-2xl border border-gray-100 p-5 shadow-xs mb-6">
          <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
            <div>
              <span className="inline-block px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-blue-50 text-blue-700 uppercase tracking-wider mb-2">
                {deck.category || 'Từ vựng'}
              </span>

              <h1 className="text-xl md:text-2xl font-extrabold text-gray-900">
                {deck.title}
              </h1>

              <p className="text-xs md:text-sm text-gray-500 mt-1 max-w-xl">
                {deck.description}
              </p>
            </div>

            <Link
              to={`/vocabulary/${deck.id}/review`}
              className="w-full sm:w-auto px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-bold text-sm rounded-xl flex items-center justify-center gap-2 shadow-sm transition-all shrink-0"
            >
              <Sparkles size={16} />
              {actionLabel}
            </Link>
          </div>

          {sessionCount > 0 && sessionCount < totalWords && (
            <p className="mt-3 text-sm text-blue-700" role="status">
              Lượt học đang dở: {sessionCount}/{totalWords} thẻ đã đánh giá. Tiếp tục để học các từ còn lại.
            </p>
          )}
          {sessionProgress && totalWords > 0 && (
            <div className="mt-4" aria-label={`Tiến độ lượt học ${sessionCount} trên ${totalWords} từ`}>
              <div className="flex justify-between text-sm font-semibold text-gray-700 mb-2">
                <span>Tiến độ lượt học</span><span>{sessionCount}/{totalWords} từ · {sessionProgress.percent}%</span>
              </div>
              <div className="w-full h-2.5 rounded-full bg-gray-100 overflow-hidden" role="progressbar" aria-valuenow={sessionCount} aria-valuemin={0} aria-valuemax={totalWords}>
                <div className="h-full bg-blue-600 rounded-full transition-all" style={{ width: `${sessionProgress.percent}%` }} />
              </div>
              <p className="text-xs text-gray-500 mt-2">{sessionProgress.completed ? 'Đã hoàn thành lượt học. Bạn có thể bắt đầu lượt mới.' : `Đã đánh giá ${sessionCount} từ, còn ${sessionProgress.remaining} từ trong lượt này.`}</p>
            </div>
          )}
          {stateWarning && <p className="mt-2 text-xs text-amber-700" role="alert">{stateWarning}</p>}
          {/* Statistics */}
          <div className="grid grid-cols-3 gap-2 mt-5 pt-4 border-t border-gray-100 text-center">
            <div className="bg-gray-50 rounded-xl p-2.5">
              <span className="text-[11px] text-gray-500 flex items-center justify-center gap-1">
                <Layers size={12} />
                Tổng số từ
              </span>

              <strong className="block text-base font-extrabold text-gray-800 mt-0.5">
                {totalWords}
              </strong>
            </div>

            <div className="bg-green-50 rounded-xl p-2.5">
              <span className="text-[11px] text-green-700 flex items-center justify-center gap-1">
                <CheckCircle size={12} />
                Đã thuộc
              </span>

              <strong className="block text-base font-extrabold text-green-800 mt-0.5">
                {statesLoading || stateWarning ? '—' : learnedCount}
              </strong>
            </div>

            <div className="bg-amber-50 rounded-xl p-2.5">
              <span className="text-[11px] text-amber-700 flex items-center justify-center gap-1">
                <Clock size={12} />
                Cần ôn lại
              </span>

              <strong className="block text-base font-extrabold text-amber-800 mt-0.5">
                {statesLoading || stateWarning ? '—' : dueCount}
              </strong>
            </div>
          </div>
        </div>

        {/* Vocabulary Groups */}
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-gray-900 flex items-center gap-2">
              <BookOpen size={18} className="text-blue-600" />
              Các nhóm từ trong bộ
            </h2>

            <span className="text-xs text-gray-500">
              {groups.length} nhóm học
            </span>
          </div>

          {groups.length === 0 ? (
            <div className="bg-gray-50 rounded-xl p-8 text-center text-sm text-gray-500">
              Bộ thẻ hiện chưa có từ vựng.
              Hãy thêm từ vựng vào bộ thẻ để bắt đầu học.
            </div>
          ) : (
            groups.map((group) => (
              <section
                key={group.id}
                className="bg-white rounded-2xl border border-gray-100 p-4 shadow-xs"
              >
                <div className="mb-3">
                  <h3 className="font-bold text-sm text-gray-800">
                    {group.title}
                  </h3>

                  <p className="text-xs text-gray-400 mt-0.5">
                    {group.description}
                  </p>
                </div>

                <div className="divide-y divide-gray-100">
                  {group.words.map((word) => (

                    <div
                      key={word.id}
                      className="py-3 flex items-start justify-between gap-3"
                    >
                      <div className="space-y-1 min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
                          <strong className="text-base font-bold text-gray-900 break-words">
                            {word.word}
                          </strong>

                          {word.phonetic && (
                            <span className="text-xs text-blue-600 font-mono break-all">
                              {word.phonetic}
                            </span>
                          )}

                          {word.partOfSpeech && (
                            <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-gray-100 text-gray-600">
                              {word.partOfSpeech}
                            </span>
                          )}

                          <button
                            type="button"
                            onClick={() => speak(word.word)}
                            aria-label={`Phát âm từ ${word.word}`}
                            className="w-6 h-6 rounded-full hover:bg-gray-100 text-gray-500 flex items-center justify-center transition-colors"
                          >
                            <Volume2 size={13} />
                          </button>
                        </div>

                        <p className="text-xs font-medium text-gray-700">
                          {word.definitionVi}
                        </p>

                        {word.exampleEn && (
                          <p className="text-[11px] text-gray-500 italic">
                            "{word.exampleEn}"
                          </p>
                        )}
                      </div>

                      <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full shrink-0 ${statusFor(states[word.id], now).style}`}>
                        {statesLoading ? 'Đang tải...' : stateWarning && !states[word.id] ? 'Không xác định' : statusFor(states[word.id], now).label}
                      </span>
                    </div>
                  ))}
                </div>
              </section>
            ))
          )}
        </div>
      </div>
    </LearnerTemplate>
  );
}
