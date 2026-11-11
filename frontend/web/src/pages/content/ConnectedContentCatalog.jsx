import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Bookmark, BookOpen } from 'lucide-react';
import LearnerTemplate from '@/components/templates/LearnerTemplate';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { useContentCatalog } from '@/features/content/hooks/useContentCatalog';
import { contentRepo } from '@/features/content/repositories/content.repo';
export default function ConnectedContentCatalog() {
  const state = useContentCatalog();
  const navigate = useNavigate();
  const [savedOnly, setSavedOnly] = useState(false);
  const [actionError, setActionError] = useState('');
  const [opening, setOpening] = useState(null);
  async function open(event, item) {
    if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    event.preventDefault(); setActionError(''); setOpening(item.id);
    try {
      try { await contentRepo.recordHistory(item.id); }
      catch (err) { if (err.status !== 403) throw err; }
      navigate(`/catalog/${item.id}`);
    }
    catch (err) { setActionError(err.message); }
    finally { setOpening(null); }
  }
  const items = state.contents.filter(item => !savedOnly || item.isBookmarked);
  return <LearnerTemplate variant="catalog"><div className="max-w-4xl mx-auto space-y-5">
    <div className="page-heading"><h1>Kho học liệu TOEIC</h1><p>Đọc và lưu những bài học phù hợp với bạn.</p></div>
    <label className="block">Tìm bài học<input className="border p-3 rounded-xl w-full mt-2" maxLength={200} value={state.searchQuery} onChange={event => state.setSearchQuery(event.target.value)} placeholder="Nhập tên hoặc nội dung bài học..." /></label>
    <div className="flex flex-wrap gap-3"><label>Danh mục<select className="border p-2 rounded-lg ml-2" value={state.selectedCategory} onChange={event => state.setSelectedCategory(event.target.value)}>{state.categories.map(name => <option key={name}>{name}</option>)}</select></label><label className="flex gap-2 items-center"><input type="checkbox" checked={savedOnly} onChange={event => setSavedOnly(event.target.checked)} />Chỉ bài đã lưu</label></div>
    {(state.error || actionError) && <p className="form-error" role="alert">{state.error || actionError}</p>}
    {state.loading ? <LoadingSpinner text="Đang tải học liệu..." /> : <>
      {!state.error && !items.length && <p className="empty-message">Chưa có học liệu phù hợp. Bài học được xuất bản sẽ hiển thị tại đây.</p>}
      <div className="grid sm:grid-cols-2 gap-4">{items.map(item => <article className="surface p-5 flex flex-col gap-3" key={item.id}>
        <div className="flex justify-between gap-3"><span className="text-sm text-slate-500">{item.category}</span><button type="button" className="icon-button" aria-label={item.isBookmarked ? 'Bỏ lưu bài học' : 'Lưu bài học'} aria-pressed={item.isBookmarked} onClick={() => state.toggleBookmark(item.id)}><Bookmark size={18} fill={item.isBookmarked ? 'currentColor' : 'none'} /></button></div>
        <h2 className="font-bold text-lg">{item.title}</h2><p className="text-sm text-slate-500">{item.description}</p>
        <Link className="pill-button navy self-start mt-auto" to={`/catalog/${item.id}`} onClick={event => open(event, item)}><BookOpen size={15} />{opening === item.id ? 'Đang mở...' : 'Đọc bài học'}</Link>
      </article>)}</div>
    </>}
  </div></LearnerTemplate>;
}
