import { useState, useEffect } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { BookOpen, FolderPlus, Layers, Target, Clock, Tag } from 'lucide-react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { DeckCover } from '@/components/molecules/DeckCover';
import { FigmaAsset } from '@/components/atoms/FigmaAsset';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { vocabularyRepo } from '@/features/vocabulary/repositories/vocabulary.repo';

export default function VocabDeckListPage() {
  const [decks, setDecks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [tag, setTag] = useState('Tất cả');
  const [search] = useSearchParams();
  useEffect(() => { let active = true; vocabularyRepo.getDecks().then(data => { if (active) setDecks(data); }).catch(err => { if (active) setError(err.message); }).finally(() => { if (active) setLoading(false); }); return () => { active = false; }; }, []);
  const query = (search.get('q') || '').toLocaleLowerCase('vi');
  const filtered = decks.filter(d => `${d.title} ${d.description}`.toLocaleLowerCase('vi').includes(query) && (tag === 'Tất cả' || d.category === tag));
  const total = decks.reduce((n,d) => n+d.totalCards,0);
  const due = decks.reduce((n,d) => n+d.dueCards,0);
  const mastered = decks.reduce((n,d) => n+d.masteredCards,0);
  return <LearnerTemplate variant="catalog"><div className="vocabulary-library">
    <div className="vocab-heading"><FigmaAsset screen="vocabulary-library" name="imgSvg17" alt="Parroto cầm sách" /><div><h1>Học Từ Vựng Tiếng Anh</h1><p>Thành thạo từ vựng tiếng Anh với hệ thống lặp lại ngắt quãng của Parroto</p></div><Link className="pill-button navy" to="/vocabulary?view=personal"><BookOpen size={14} />Sổ tay từ vựng</Link></div>
    <section className="vocab-statistics surface"><div><h2>Thống Kê Học Tập</h2><div className="vocab-stat-grid"><div><Target /><p>Tổng số Thẻ<strong>{total}</strong></p></div><div><Clock /><p>Đến Hạn<strong>{due}</strong></p></div><div><Layers /><p>Tổng số lần Ôn tập<strong>{mastered}</strong></p></div><div><Target /><p>Độ thành thạo<strong>{total ? Math.round(mastered / total * 100) : 0}%</strong></p></div></div><Link className="vocab-race" to={decks[0] ? `/vocabulary/${decks[0].id}/review` : '/vocabulary'}>ϟ Bắt Đầu Ôn Từ Vựng</Link></div><div><h2>Trạng thái Từ vựng</h2><div className="vocab-status-bars">{[['Đang Học', total-mastered, 'cyan'], ['Đang Ôn tập', due, 'blue'], ['Đã Thành thạo', mastered, 'green'], ['Tổng số Thẻ', total, 'red']].map(([name, value, color]) => <div key={name}><strong className={color}>{value}</strong><span>{name}</span></div>)}</div></div></section>
    <section className="personal-decks surface"><div className="personal-decks-heading"><BookOpen size={23} /><div><h2>BỘ THẺ CỦA TÔI</h2><p>Bộ thẻ cá nhân để bắt đầu quản lý từ vựng của bạn</p></div></div><div className="personal-decks-empty"><FolderPlus size={48} /><h3>Chưa có bộ thẻ nào</h3><p>Bắt đầu học với các bộ thẻ trong thư viện bên dưới.</p><a className="pill-button navy" href="#vocab-library">Khám phá thư viện</a></div></section>
    <div className="vocab-tags"><p><Tag size={14} />Lọc theo Tags</p><div>{['Tất cả', ...new Set(decks.map(d => d.category))].map(t => <button key={t} type="button" className={`pill-button ${tag === t ? 'navy' : ''}`} aria-pressed={tag === t} onClick={() => setTag(t)}>{t === 'Tất cả' ? t : `#${t}`}</button>)}</div></div>
    <section id="vocab-library"><div className="section-heading"><h2>Từ Vựng TOEIC <small>({filtered.length} bộ thẻ)</small></h2></div>{loading && <LoadingSpinner text="Đang tải các bộ từ vựng..." />}{error && <p className="form-error" role="alert">{error}</p>}{!loading && !filtered.length && <p className="empty-message">Không tìm thấy bộ thẻ phù hợp.</p>}<div className="vocab-deck-grid">{filtered.map((deck,i) => <article className="vocab-deck surface" key={deck.id}><Link to={`/vocabulary/${deck.id}`} className="block cursor-pointer"><DeckCover title={deck.title} index={i} category={deck.category} /></Link><div className="vocab-deck-body"><Link to={`/vocabulary/${deck.id}`} className="hover:text-blue-600 transition-colors"><h3>{deck.title}</h3></Link><p><Layers size={12} />{deck.totalCards} thẻ <span>Đến hạn: {deck.dueCards}</span></p><div className="flex gap-2 mt-2"><Link className="pill-button navy flex-1 text-center" to={`/vocabulary/${deck.id}/review`}>ÔN TẬP</Link><Link className="pill-button flex-1 text-center border border-gray-200 text-gray-700 hover:bg-gray-50" to={`/vocabulary/${deck.id}`}>XEM TỪ</Link></div></div></article>)}</div></section>
  </div></LearnerTemplate>;
}
