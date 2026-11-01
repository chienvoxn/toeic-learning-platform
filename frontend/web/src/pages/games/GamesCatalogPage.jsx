import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Gamepad2, Zap, Headphones, Trophy, Play, Lock, Sparkles } from 'lucide-react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { gameRepo } from '@/features/games/repositories/game.repo';

export default function GamesCatalogPage() {
  const [games, setGames] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    gameRepo.getGameCatalog().then((res) => {
      if (active) {
        setGames(res);
        setLoading(false);
      }
    });
    return () => {
      active = false;
    };
  }, []);

  const getIcon = (iconName) => {
    switch (iconName) {
      case 'Zap':
        return <Zap size={22} className="text-amber-500" />;
      case 'Headphones':
        return <Headphones size={22} className="text-blue-500" />;
      case 'Trophy':
        return <Trophy size={22} className="text-purple-500" />;
      default:
        return <Gamepad2 size={22} className="text-indigo-500" />;
    }
  };

  return (
    <LearnerTemplate variant="catalog">
      <div className="max-w-xl mx-auto px-4 py-4 pb-24 md:pb-12">
        {/* Banner matching Figma game_margin */}
        <div className="bg-gradient-to-br from-indigo-900 via-blue-900 to-indigo-800 rounded-2xl p-5 text-white shadow-md mb-6">
          <div className="flex items-center gap-1.5 text-xs font-semibold text-blue-200 uppercase tracking-wider mb-1">
            <Sparkles size={14} /> Minigame Tiếng Anh
          </div>
          <h1 className="text-2xl font-extrabold tracking-tight">Khu Vực Trò Chơi</h1>
          <p className="text-xs text-blue-100 mt-1">
            Vừa chơi vừa học! Thử thách phản xạ từ vựng, tăng cường trí nhớ thông qua các trò chơi tương tác ngắn.
          </p>
        </div>

        {loading ? (
          <LoadingSpinner text="Đang tải danh sách minigame..." />
        ) : (
          <div className="space-y-4">
            {games.map((game) => (
              <article
                key={game.id}
                className={`bg-white rounded-2xl border p-4 shadow-xs transition-all flex flex-col justify-between ${
                  game.active
                    ? 'border-gray-100 hover:border-blue-200 hover:shadow-md'
                    : 'border-gray-100 opacity-60 bg-gray-50/50'
                }`}
              >
                <div className="flex items-start gap-3.5 mb-3">
                  <div className="w-12 h-12 rounded-xl bg-gray-50 flex items-center justify-center shrink-0 border border-gray-100">
                    {getIcon(game.icon)}
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-0.5">
                      <h2 className="font-extrabold text-sm text-gray-900 truncate">{game.title}</h2>
                      {game.active && (
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-green-100 text-green-700">
                          Sẵn sàng
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-gray-500 leading-relaxed">{game.description}</p>
                  </div>
                </div>

                <div className="flex items-center justify-between pt-3 border-t border-gray-100 text-xs">
                  <div className="flex items-center gap-3 text-gray-400 text-[11px]">
                    <span>⏱ {game.playTime}</span>
                    <span>•</span>
                    <span>🎯 Cấp độ: {game.difficulty}</span>
                  </div>

                  {game.active ? (
                    <Link
                      to="/games/word-match"
                      className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl flex items-center gap-1.5 shadow-2xs transition-colors"
                    >
                      <Play size={13} fill="currentColor" /> Chơi ngay
                    </Link>
                  ) : (
                    <span className="px-3 py-1.5 bg-gray-100 text-gray-400 font-medium rounded-lg flex items-center gap-1 text-[11px]">
                      <Lock size={12} /> Sắp ra mắt
                    </span>
                  )}
                </div>
              </article>
            ))}
          </div>
        )}
      </div>
    </LearnerTemplate>
  );
}
