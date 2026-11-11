import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { BookOpen } from 'lucide-react';
import AssessmentLayout from '@/components/templates/AssessmentLayout';
import { TestMetadata } from '@/components/molecules/TestMetadata';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { assessmentRepo } from '@/features/assessment/repositories/assessment.repo';

export default function TestCatalogPage() {
  const [tests, setTests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [page, setPage] = useState(0), [hasMore, setHasMore] = useState(false);
  useEffect(() => {
    let active = true;
    assessmentRepo.getCatalog().then(data => { if (active) { setTests(data); setHasMore(data.filter(t => t.remote).length === 50); } }).catch(err => { if (active) setError(err.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);
  return <AssessmentLayout variant="catalog"><div className="test-catalog-page"><p className="rounded-xl border border-blue-100 bg-blue-50 p-4 mb-5 text-sm text-blue-900">Đề đã xuất bản: đăng nhập để lưu bài và kết quả theo tài khoản. Đề Reading mẫu dành cho khách chỉ lưu trên trình duyệt. <Link to="/exam-history">Xem lịch sử thi</Link></p>
    <section className="test-collection-hero"><div className="collection-tags"><span>TOEIC</span><span>2026</span></div><h1>Bộ đề TOEIC</h1><p>{tests.length} đề thi · Luyện tập theo từng phần</p><BookOpen aria-hidden="true" /></section>
    <h2 className="eyebrow">DANH SÁCH ĐỀ</h2>
    {loading && <LoadingSpinner text="Đang tải danh sách đề thi..." />}{error && <p className="form-error" role="alert">{error}</p>}
    <div className="test-catalog-grid">{tests.map(test => <article className="test-list-card surface" key={test.id}><div><h3>{test.title}<span className="free-label">MIỄN PHÍ</span></h3><TestMetadata test={test} /></div><Link className="pill-button" to={`/tests/${test.id}/overview`}>XEM CHI TIẾT</Link></article>)}</div>
  <div style={{marginTop:20}}>{hasMore && <button className="pill-button" disabled={loading} onClick={async () => { setLoading(true); try { const next = await assessmentRepo.getCatalog(page + 1); setTests(t => [...t, ...next]); setPage(n => n + 1); setHasMore(next.length === 50); } catch (e) { setError(e.message); } finally { setLoading(false); } }}>Tải thêm đề</button>}</div></div></AssessmentLayout>;
}
