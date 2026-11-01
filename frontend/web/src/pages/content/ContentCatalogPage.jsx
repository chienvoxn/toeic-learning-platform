import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Search, Bookmark, Play, Volume2, BookOpen, Clock, Eye, Sparkles } from 'lucide-react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { useContentCatalog } from '@/features/content/hooks/useContentCatalog';

export default function ContentCatalogPage() {
  const {
    contents,
    categories,
    selectedCategory,
    setSelectedCategory,
    selectedDifficulty,
    setSelectedDifficulty,
    searchQuery,
    setSearchQuery,
    loading,
    error,
    toggleBookmark,
  } = useContentCatalog();

  const [activeTabSection, setActiveTabSection] = useState('all'); // all, trending, saved

  const displayedContents = contents.filter((item) => {
    if (activeTabSection === 'saved') return item.isBookmarked;
    if (activeTabSection === 'trending') return item.totalViews >= 2000;
    return true;
  });

  return (
    <LearnerTemplate variant="catalog">
      <div className="mobile-catalog-page max-w-4xl mx-auto px-4 py-4 pb-24 md:pb-12">
        {/* Header Hero matching Figma MainScreen */}
        <div className="bg-gradient-to-r from-blue-600 via-indigo-600 to-sky-500 rounded-2xl p-5 text-white shadow-md mb-6">
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-blue-100 mb-1">
            <Sparkles size={14} /> Kho Học Liệu Đa Phương Tiện
          </div>
          <h1 className="text-2xl md:text-3xl font-extrabold tracking-tight">Khám Phá Bài Học TOEIC</h1>
          <p className="text-sm text-blue-100 mt-1 max-w-xl">
            Luyện nghe, shadowing và từ vựng qua trích đoạn phim, talkshow, podcast, bản tin và đề thi thực tế.
          </p>

          {/* Search bar */}
          <div className="relative mt-4">
            <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-400" size={17} />
            <input
              type="text"
              placeholder="Tìm kiếm chủ đề, bài hát, podcast, part 1-7..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-white text-gray-800 text-sm placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-300 shadow-inner"
            />
          </div>
        </div>

        {/* Section Tabs (Tất cả / Thịnh hành / Đã lưu) */}
        <div className="flex items-center gap-2 mb-4 border-b border-gray-100 pb-2 overflow-x-auto no-scrollbar">
          <button
            type="button"
            onClick={() => setActiveTabSection('all')}
            className={`px-4 py-1.5 rounded-full text-xs font-bold transition-all ${
              activeTabSection === 'all'
                ? 'bg-blue-600 text-white shadow-sm'
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}
          >
            Tất cả bài học
          </button>
          <button
            type="button"
            onClick={() => setActiveTabSection('trending')}
            className={`px-4 py-1.5 rounded-full text-xs font-bold transition-all ${
              activeTabSection === 'trending'
                ? 'bg-blue-600 text-white shadow-sm'
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}
          >
            🔥 Thịnh hành
          </button>
          <button
            type="button"
            onClick={() => setActiveTabSection('saved')}
            className={`px-4 py-1.5 rounded-full text-xs font-bold transition-all ${
              activeTabSection === 'saved'
                ? 'bg-blue-600 text-white shadow-sm'
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}
          >
            🔖 Đã đánh dấu ({contents.filter((c) => c.isBookmarked).length})
          </button>
        </div>

        {/* Horizontal Category Pill Bar (Figma: Phim, Hội thoại, TOEIC, Podcasts...) */}
        <div className="mb-4">
          <div className="flex gap-2 overflow-x-auto pb-2 no-scrollbar">
            {categories.map((cat) => (
              <button
                key={cat}
                type="button"
                onClick={() => setSelectedCategory(cat)}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-colors border ${
                  selectedCategory === cat
                    ? 'border-blue-600 bg-blue-50 text-blue-700 font-semibold shadow-xs'
                    : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-50'
                }`}
              >
                {cat}
              </button>
            ))}
          </div>
        </div>

        {/* Difficulty Filter */}
        <div className="flex flex-wrap items-center gap-2 mb-6 text-xs text-gray-500">
          <span>Cấp độ:</span>
          {['Tất cả', 'A1', 'A2', 'B1', 'B2', 'C1'].map((diff) => (
            <button
              key={diff}
              type="button"
              onClick={() => setSelectedDifficulty(diff)}
              className={`px-2.5 py-1 rounded-md transition-colors ${
                selectedDifficulty === diff
                  ? 'bg-gray-800 text-white font-bold'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              {diff}
            </button>
          ))}
        </div>

        {/* Content Status & List */}
        {loading && <LoadingSpinner text="Đang tải danh mục học liệu..." />}
        {error && <p className="text-red-500 text-sm text-center py-6">{error}</p>}

        {!loading && !error && displayedContents.length === 0 && (
          <div className="text-center py-12 bg-white rounded-2xl border border-gray-100 p-8 shadow-xs">
            <BookOpen className="mx-auto text-gray-300 mb-3" size={48} />
            <h3 className="text-base font-bold text-gray-700">Không tìm thấy nội dung phù hợp</h3>
            <p className="text-xs text-gray-500 mt-1">Hãy thử đổi danh mục hoặc từ khóa tìm kiếm.</p>
          </div>
        )}

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {displayedContents.map((item) => (
            <article
              key={item.id}
              className="bg-white rounded-xl border border-gray-100 overflow-hidden shadow-xs hover:shadow-md transition-shadow flex flex-col group"
            >
              {/* Thumbnail Container */}
              <div className="relative aspect-video w-full bg-gray-100 overflow-hidden">
                <img
                  src={item.thumbnailUrl}
                  alt={item.title}
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                  loading="lazy"
                />
                {/* Media type icon */}
                <div className="absolute inset-0 bg-black/20 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                  <div className="w-10 h-10 rounded-full bg-white/90 text-blue-600 flex items-center justify-center shadow-lg">
                    {item.mediaType === 'audio' ? <Volume2 size={20} /> : <Play size={20} className="ml-0.5" />}
                  </div>
                </div>

                {/* Level badge */}
                <span className="absolute top-2 left-2 px-2 py-0.5 rounded text-[11px] font-bold bg-black/60 text-white backdrop-blur-xs">
                  {item.difficulty}
                </span>

                {/* Duration */}
                <span className="absolute bottom-2 right-2 px-1.5 py-0.5 rounded text-[10px] font-medium bg-black/70 text-white flex items-center gap-1 backdrop-blur-xs">
                  <Clock size={10} /> {item.durationMinutes}p
                </span>

                {/* Bookmark Button */}
                <button
                  type="button"
                  onClick={() => toggleBookmark(item.id)}
                  aria-label="Đánh dấu bài học"
                  className={`absolute top-2 right-2 w-7 h-7 rounded-full flex items-center justify-center backdrop-blur-xs transition-colors ${
                    item.isBookmarked
                      ? 'bg-amber-500 text-white shadow-sm'
                      : 'bg-black/40 text-white hover:bg-black/60'
                  }`}
                >
                  <Bookmark size={13} fill={item.isBookmarked ? 'currentColor' : 'none'} />
                </button>
              </div>

              {/* Card Body */}
              <div className="p-3.5 flex flex-col flex-1 justify-between">
                <div>
                  <div className="flex items-center gap-2 mb-1.5 text-[11px] text-gray-500">
                    <span className="font-semibold text-blue-600 uppercase tracking-wider">{item.category}</span>
                    <span>•</span>
                    <span>Part {item.part}</span>
                    <span>•</span>
                    <span className="text-gray-400">{item.sourcePlatform}</span>
                  </div>

                  <h3 className="font-bold text-sm text-gray-900 line-clamp-2 leading-snug group-hover:text-blue-600 transition-colors">
                    {item.title}
                  </h3>

                  <p className="text-xs text-gray-500 line-clamp-2 mt-1 leading-relaxed">
                    {item.description}
                  </p>
                </div>

                <div className="mt-3 pt-3 border-t border-gray-100 flex items-center justify-between text-xs">
                  <span className="text-gray-400 flex items-center gap-1 text-[11px]">
                    <Eye size={12} /> {item.totalViews.toLocaleString()} lượt xem
                  </span>
                  <Link
                    to={`/listening?view=exercise&contentId=${item.id}`}
                    className="font-bold text-blue-600 hover:text-blue-700 flex items-center gap-1 text-xs"
                  >
                    Bắt đầu học →
                  </Link>
                </div>
              </div>
            </article>
          ))}
        </div>
      </div>
    </LearnerTemplate>
  );
}
