import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { ChevronLeft, Layers, Volume2, Sparkles, BookOpen, CheckCircle, Clock } from 'lucide-react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { vocabularyRepo } from '@/features/vocabulary/repositories/vocabulary.repo';
import VocabularyEditor from '@/components/organisms/VocabularyEditor';
import { useMock } from '@/lib/runtime-config';

export default function VocabDeckDetailPage() {
  const { deckId } = useParams();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    let active = true;
    vocabularyRepo
      .getDeckDetail(deckId)
      .then((res) => {
        if (active) setData(res);
      })
      .catch((err) => {
        if (active) setError(err.message || 'Không thể tải chi tiết bộ từ vựng.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [deckId, revision]);

  const speak = (text) => {
    if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel();
      const utterance = new SpeechSynthesisUtterance(text);
      utterance.lang = 'en-US';
      utterance.rate = 0.9;
      window.speechSynthesis.speak(utterance);
    }
  };

  if (loading) {
    return (
      <LearnerTemplate variant="catalog">
        <div className="py-12">
          <LoadingSpinner text="Đang tải danh sách nhóm từ vựng..." />
        </div>
      </LearnerTemplate>
    );
  }

  if (error || !data) {
    return (
      <LearnerTemplate variant="catalog">
        <div className="max-w-md mx-auto py-12 px-4 text-center">
          <p className="text-red-500 mb-4">{error || 'Không tìm thấy bộ từ vựng.'}</p>
          <Link to="/vocabulary" className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-semibold">
            Quay lại thư viện
          </Link>
        </div>
      </LearnerTemplate>
    );
  }

  const { deck, groups, totalWords, masteredCount, learningCount } = data;

  return (
    <LearnerTemplate variant="catalog">
      <div className="vocab-deck-detail max-w-3xl mx-auto px-4 py-4 pb-24 md:pb-12">
        {/* Navigation Breadcrumb */}
        <Link
          to="/vocabulary"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-gray-500 hover:text-blue-600 transition-colors mb-4"
        >
          <ChevronLeft size={16} /> Quay lại danh sách bộ từ vựng
        </Link>

        {/* Deck Header Banner */}
        <div className="bg-white rounded-2xl border border-gray-100 p-5 shadow-xs mb-6">
          <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
            <div>
              <span className="inline-block px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-blue-50 text-blue-700 uppercase tracking-wider mb-2">
                {deck.category || 'Từ vựng'}
              </span>
              <h1 className="text-xl md:text-2xl font-extrabold text-gray-900">{deck.title}</h1>
              <p className="text-xs md:text-sm text-gray-500 mt-1 max-w-xl">{deck.description}</p>
            </div>

            <Link
              to={`/vocabulary/${deck.id}/review`}
              className="w-full sm:w-auto px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-bold text-sm rounded-xl flex items-center justify-center gap-2 shadow-sm transition-all shrink-0"
            >
              <Sparkles size={16} /> BẮT ĐẦU ÔN TẬP
            </Link>
          </div>

          {/* Quick Stats Grid */}
          <div className="grid grid-cols-3 gap-2 mt-5 pt-4 border-t border-gray-100 text-center">
            <div className="bg-gray-50 rounded-xl p-2.5">
              <span className="text-[11px] text-gray-500 flex items-center justify-center gap-1">
                <Layers size={12} /> Tổng số từ
              </span>
              <strong className="block text-base font-extrabold text-gray-800 mt-0.5">{totalWords}</strong>
            </div>
            <div className="bg-green-50 rounded-xl p-2.5">
              <span className="text-[11px] text-green-700 flex items-center justify-center gap-1">
                <CheckCircle size={12} /> Đã thuộc
              </span>
              <strong className="block text-base font-extrabold text-green-800 mt-0.5">{masteredCount}</strong>
            </div>
            <div className="bg-amber-50 rounded-xl p-2.5">
              <span className="text-[11px] text-amber-700 flex items-center justify-center gap-1">
                <Clock size={12} /> Cần ôn lại
              </span>
              <strong className="block text-base font-extrabold text-amber-800 mt-0.5">{deck.dueCards || learningCount}</strong>
            </div>
          </div>
        </div>

        {!useMock && <VocabularyEditor deckId={deckId} onSaved={() => setRevision(value => value + 1)} />}
        {/* Word Groups Section (matching Frame 6.png) */}
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-gray-900 flex items-center gap-2">
              <BookOpen size={18} className="text-blue-600" /> Các nhóm từ trong bộ
            </h2>
            <span className="text-xs text-gray-500">{groups.length} nhóm học</span>
          </div>

          {groups.map((group) => (
            <section key={group.id} className="bg-white rounded-2xl border border-gray-100 p-4 shadow-xs">
              <div className="mb-3">
                <h3 className="font-bold text-sm text-gray-800">{group.title}</h3>
                <p className="text-xs text-gray-400 mt-0.5">{group.description}</p>
              </div>

              <div className="divide-y divide-gray-100">
                {group.words.map((word) => (
                  <div key={word.id} className="py-3 flex items-start justify-between gap-3">
                    <div className="space-y-1 min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
                        <strong className="text-base font-bold text-gray-900 break-words">{word.word}</strong>
                        <span className="text-xs text-blue-600 font-mono break-all">{word.phonetic}</span>
                        <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-gray-100 text-gray-600">
                          {word.partOfSpeech}
                        </span>
                        <button
                          type="button"
                          onClick={() => speak(word.word)}
                          aria-label={`Phát âm từ ${word.word}`}
                          className="w-6 h-6 rounded-full hover:bg-gray-100 text-gray-500 flex items-center justify-center transition-colors"
                        >
                          <Volume2 size={13} />
                        </button>
                      </div>

                      <p className="text-xs font-medium text-gray-700">{word.definitionVi}</p>
                      {word.exampleEn && (
                        <p className="text-[11px] text-gray-500 italic">"{word.exampleEn}"</p>
                      )}
                    </div>

                    <span
                      className={`text-[10px] font-bold px-2 py-0.5 rounded-full shrink-0 ${
                        word.status === 'mastered'
                          ? 'bg-green-100 text-green-700'
                          : word.status === 'learning'
                          ? 'bg-blue-100 text-blue-700'
                          : 'bg-gray-100 text-gray-600'
                      }`}
                    >
                      {word.status === 'mastered'
                        ? 'Đã thuộc'
                        : word.status === 'learning'
                        ? 'Đang học'
                        : 'Mới'}
                    </span>
                  </div>
                ))}
              </div>
            </section>
          ))}
        </div>
      </div>
    </LearnerTemplate>
  );
}
