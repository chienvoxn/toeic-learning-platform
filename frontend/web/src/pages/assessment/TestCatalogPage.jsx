import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { BookOpen } from 'lucide-react';
import AssessmentLayout from '@/components/templates/AssessmentLayout';
import { GuestPracticeNotice } from '@/components/molecules/GuestPracticeNotice';
import { TestMetadata } from '@/components/molecules/TestMetadata';
import { LoadingSpinner } from '@/components/atoms/LoadingSpinner';
import { assessmentRepo } from '@/features/assessment/repositories/assessment.repo';

export default function TestCatalogPage() {
  const [tests, setTests] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    assessmentRepo.getCatalog().then(data => { if (active) setTests(data); }).catch(err => { if (active) setError(err.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);
  return <AssessmentLayout variant="catalog"><div className="test-catalog-page"><GuestPracticeNotice />
    <section className="test-collection-hero"><div className="collection-tags"><span>TOEIC</span><span>2026</span></div><h1>Bộ đề TOEIC</h1><p>{tests.length} đề thi · Luyện tập theo từng phần</p><BookOpen aria-hidden="true" /></section>
    <h2 className="eyebrow">DANH SÁCH ĐỀ</h2>
    {loading && <LoadingSpinner text="Đang tải danh sách đề thi..." />}{error && <p className="form-error" role="alert">{error}</p>}
    <div className="test-catalog-grid">{tests.map(test => <article className="test-list-card surface" key={test.id}><div><h3>{test.title}<span className="free-label">MIỄN PHÍ</span></h3><TestMetadata test={test} /></div><Link className="pill-button" to={`/tests/${test.id}/overview`}>XEM CHI TIẾT</Link></article>)}</div>
  </div></AssessmentLayout>;
}
