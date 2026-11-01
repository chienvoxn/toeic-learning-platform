import { Link } from 'react-router-dom';
import { ChevronLeft, Clock, Zap, RotateCcw, Trophy, Award, Sparkles, CheckCircle2 } from 'lucide-react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { useWordMatch } from '@/features/games/hooks/useWordMatch';

export default function WordMatchGamePage() {
  const {
    gameState,
    cards,
    selectedCards,
    clearedPairIds,
    mismatchedCardIds,
    score,
    streak,
    timeLeft,
    loading,
    summary,
    startGame,
    handleCardClick,
    restartGame,
  } = useWordMatch();

  const isTimeRunningOut = timeLeft <= 10 && gameState === 'playing';

  return (
    <LearnerTemplate variant="catalog">
      <div className="max-w-md mx-auto px-4 py-4 pb-24 md:pb-12 min-h-[90vh] flex flex-col justify-between">
        {/* Top Bar Navigation */}
        <div className="flex items-center justify-between mb-4">
          <Link
            to="/games"
            className="inline-flex items-center gap-1 text-xs font-semibold text-gray-500 hover:text-gray-900"
          >
            <ChevronLeft size={16} /> Danh mục game
          </Link>
          <div className="flex items-center gap-1.5 text-xs font-extrabold text-blue-600 bg-blue-50 px-3 py-1 rounded-full">
            <Trophy size={13} /> Điểm cao: {summary?.highScore ?? 0}
          </div>
        </div>

        {/* Game Stats Panel (Timer & Score) */}
        <div className="bg-white rounded-2xl border border-gray-100 p-4 shadow-xs mb-4">
          <div className="flex items-center justify-between">
            {/* Timer (Figma mobile-screen-4 pulsing red when <= 10s) */}
            <div
              className={`flex items-center gap-2 px-3 py-1.5 rounded-xl font-mono text-base font-extrabold transition-all ${
                isTimeRunningOut
                  ? 'bg-red-50 text-red-600 animate-pulse border border-red-200'
                  : 'bg-gray-100 text-gray-800'
              }`}
            >
              <Clock size={16} />
              <span>00:{timeLeft < 10 ? `0${timeLeft}` : timeLeft}</span>
            </div>

            {/* Score */}
            <div className="text-right">
              <span className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider block">Điểm số</span>
              <strong className="text-xl font-extrabold text-gray-900 leading-none">{score}</strong>
            </div>

            {/* Streak Combo */}
            <div className="flex items-center gap-1 text-xs font-bold text-amber-500 bg-amber-50 px-2.5 py-1 rounded-lg">
              <Zap size={14} fill="currentColor" /> x{streak > 0 ? streak : 1}
            </div>
          </div>
        </div>

        {/* Board Container */}
        {loading ? (
          <div className="py-20 flex-1 flex items-center justify-center">
            <LoadingSpinner text="Đang trộn thẻ từ vựng..." />
          </div>
        ) : (
          <div className="flex-1 flex flex-col justify-center">
            {/* State 1: READY (div.mobile-screen.png) */}
            {gameState === 'ready' && (
              <div className="bg-white rounded-3xl border border-gray-100 p-6 text-center shadow-md my-auto space-y-4">
                <div className="w-16 h-16 mx-auto rounded-2xl bg-gradient-to-tr from-blue-600 to-indigo-500 text-white flex items-center justify-center shadow-md">
                  <Sparkles size={32} />
                </div>
                <h2 className="text-xl font-extrabold text-gray-900">Nối Từ Siêu Tốc</h2>
                <p className="text-xs text-gray-500 leading-relaxed max-w-xs mx-auto">
                  Hãy ghép cặp từ tiếng Anh với nghĩa tiếng Việt tương ứng thật nhanh. Mỗi cặp ghép đúng liên tiếp sẽ nhân đôi điểm combo!
                </p>
                <div className="pt-2">
                  <button
                    type="button"
                    onClick={startGame}
                    className="w-full py-3.5 bg-blue-600 hover:bg-blue-700 text-white font-extrabold text-sm rounded-2xl shadow-sm transition-all"
                  >
                    BẮT ĐẦU CHƠI NGAY (45s)
                  </button>
                </div>
              </div>
            )}

            {/* State 2 & 3 & 4: PLAYING GRID (mobile-screen-1, 2, 3) */}
            {(gameState === 'playing' || gameState === 'time-up' || gameState === 'completed') && (
              <div className="grid grid-cols-2 gap-2.5 my-auto">
                {cards.map((card) => {
                  const isCleared = clearedPairIds.has(card.pairId);
                  const isSelected = selectedCards.some((c) => c.cardId === card.cardId);
                  const isMismatched = mismatchedCardIds.includes(card.cardId);

                  return (
                    <button
                      key={card.cardId}
                      type="button"
                      disabled={isCleared || gameState !== 'playing'}
                      onClick={() => handleCardClick(card)}
                      className={`min-h-[78px] p-3 rounded-2xl border-2 text-center flex items-center justify-center font-bold text-xs md:text-sm transition-all duration-200 select-none ${
                        isCleared
                          ? 'border-green-200 bg-green-50 text-green-600 opacity-40 scale-95 pointer-events-none'
                          : isMismatched
                          ? 'border-red-400 bg-red-50 text-red-700 animate-shake'
                          : isSelected
                          ? 'border-blue-600 bg-blue-50 text-blue-700 shadow-md scale-102 ring-2 ring-blue-300'
                          : 'border-gray-200 bg-white text-gray-800 hover:border-gray-300 shadow-2xs active:scale-98'
                      }`}
                    >
                      <span>{card.text}</span>
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* State 5 & 6: TIME-UP or COMPLETED MODAL (mobile-screen-5.png) */}
        {(gameState === 'time-up' || gameState === 'completed') && (
          <div className="fixed inset-0 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4 z-50 animate-fade-in">
            <div className="bg-white rounded-3xl p-6 w-full max-w-sm text-center shadow-xl space-y-4">
              <div
                className={`w-14 h-14 mx-auto rounded-full flex items-center justify-center ${
                  gameState === 'completed'
                    ? 'bg-green-100 text-green-600'
                    : 'bg-amber-100 text-amber-600'
                }`}
              >
                {gameState === 'completed' ? <CheckCircle2 size={32} /> : <Award size={32} />}
              </div>

              <div>
                <h3 className="text-xl font-extrabold text-gray-900">
                  {gameState === 'completed' ? 'Xuất Sắc! Hoàn Thành!' : 'Hết Giờ Rồi!'}
                </h3>
                <p className="text-xs text-gray-500 mt-1">
                  {gameState === 'completed'
                    ? 'Bạn đã ghép đúng toàn bộ các cặp từ vựng!'
                    : 'Rất tiếc thời gian đã hết, cùng luyện tập lại nhé.'}
                </p>
              </div>

              <div className="bg-gray-50 rounded-2xl p-4 space-y-2 text-sm">
                <div className="flex justify-between text-gray-600">
                  <span>Điểm đạt được:</span>
                  <strong className="text-gray-900 font-extrabold text-base">{score}</strong>
                </div>
                <div className="flex justify-between text-gray-600">
                  <span>Số cặp ghép đúng:</span>
                  <strong className="text-gray-900">{clearedPairIds.size} cặp</strong>
                </div>
                {summary?.isNewHigh && (
                  <div className="text-xs font-bold text-amber-600 bg-amber-50 py-1 rounded-md">
                    🎉 Kỷ lục cá nhân mới!
                  </div>
                )}
              </div>

              <div className="flex gap-2 pt-2">
                <button
                  type="button"
                  onClick={restartGame}
                  className="flex-1 py-3 bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs rounded-xl flex items-center justify-center gap-1.5 transition-colors shadow-xs"
                >
                  <RotateCcw size={14} /> Chơi lại
                </button>
                <Link
                  to="/games"
                  className="flex-1 py-3 bg-gray-100 hover:bg-gray-200 text-gray-700 font-bold text-xs rounded-xl flex items-center justify-center transition-colors"
                >
                  Thoát
                </Link>
              </div>
            </div>
          </div>
        )}
      </div>
    </LearnerTemplate>
  );
}
