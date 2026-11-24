import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { BookOpen, Clock3, Headphones, ArrowRight } from 'lucide-react';
import AssessmentLayout from '@/components/templates/AssessmentLayout';
import { remoteAssessmentRepo } from '@/features/assessment/repositories/assessment.repo';
import { useAuth } from '@/features/auth/hooks/useAuth';
import { assessmentRepo } from '@/features/assessment/repositories/assessment.repo';
import { GuestPracticeNotice } from '@/components/molecules/GuestPracticeNotice';
import { TestMetadata } from '@/components/molecules/TestMetadata';

const practiceSets = [
  { part: 1, title: 'Photographs', section: 'Listening', count: 6 },
  { part: 2, title: 'Question–Response', section: 'Listening', count: 25 },
  { part: 3, title: 'Conversations', section: 'Listening', count: 39 },
  { part: 4, title: 'Talks', section: 'Listening', count: 30 },
  { part: 5, title: 'Incomplete Sentences', section: 'Reading', count: 30 },
  { part: 6, title: 'Text Completion', section: 'Reading', count: 16 },
  { part: 7, title: 'Reading Comprehension', section: 'Reading', count: 54 },
];

const gridStyle = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 280px), 1fr))',
  gap: '20px',
  marginTop: '20px',
  marginBottom: '30px'
};

const cardStyle = {
  padding: '24px',
  borderRadius: '16px',
  display: 'flex',
  flexDirection: 'column',
  gap: '14px',
  justifyContent: 'space-between',
  minHeight: '235px'
};

export default function TestCatalogPage() {
  const { user } = useAuth();
  const [guestTests, setGuestTests] = useState([]);
  const [fullTests, setFullTests] = useState([]);
  const [loading, setLoading] = useState(() => Boolean(user));
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const isAuthenticated = Boolean(user);

  useEffect(() => { let active = true; assessmentRepo.getCatalog().then(items => { if (active) setGuestTests(items); }).catch(() => {}); return () => { active = false; }; }, []);
  function retryLoad() {
    setLoading(true);
    setError('');
    setReloadKey(value => value + 1);
  }

  useEffect(() => {
    if (!isAuthenticated) return undefined;
    let active = true;

    remoteAssessmentRepo.listFullTests()
      .then(items => {
        if (active) setFullTests(Array.isArray(items) ? items : []);
      })
      .catch(err => {
        if (active) setError(err.message || 'Không thể tải thư viện đề thi.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => { active = false; };
  }, [isAuthenticated, reloadKey]);

  return (
    <AssessmentLayout variant="catalog">
      <div className="test-catalog-page">
        <section className="test-collection-hero">
          <div className="collection-tags">
            <span>ENGLINGO</span>
            <span>TOEIC 2026</span>
          </div>
          <h1>Thư viện đề thi TOEIC</h1>
          <p>
            Chọn một bộ đề hoặc phần luyện tập để xem chi tiết.
          </p>
          <BookOpen aria-hidden="true" />
        </section>

        <div style={{ marginTop: '30px' }}>
          <h2>Đề thi thử đầy đủ</h2>
          <p className="text-slate-500">
            Bộ đề mô phỏng đã xuất bản trên hệ thống.
          </p>

          <div style={gridStyle}>
            {loading && [1, 2].map(index => (
              <article aria-hidden="true" className="surface animate-pulse" style={cardStyle} key={index}>
                <div className="space-y-3">
                  <div className="h-5 w-24 rounded bg-slate-200" />
                  <div className="h-6 w-4/5 rounded bg-slate-200" />
                  <div className="h-4 w-full rounded bg-slate-100" />
                  <div className="h-4 w-2/3 rounded bg-slate-100" />
                </div>
                <div className="h-10 w-28 rounded-full bg-slate-200" />
              </article>
            ))}
            {!loading && !isAuthenticated && (
              <article className="surface" style={cardStyle}>
                <div>
                  <span className="free-label">FULL TEST</span>
                  <h3 className="mt-3">Đăng nhập để xem đề thi đã xuất bản</h3>
                  <p className="mt-3 text-slate-500">
                    Full Test được tạo thành phiên làm bài cá nhân và lưu đáp án trên tài khoản của bạn.
                  </p>
                </div>
                <Link className="pill-button" to="/login" state={{ from: '/tests' }}>
                  ĐĂNG NHẬP <ArrowRight size={15} className="inline" />
                </Link>
              </article>
            )}
            {!loading && isAuthenticated && error && (
              <article className="surface" style={cardStyle} role="alert">
                <div>
                  <span className="free-label">KHÔNG TẢI ĐƯỢC</span>
                  <h3 className="mt-3">Không thể tải thư viện Full Test</h3>
                  <p className="mt-3 text-slate-500">{error}</p>
                </div>
                <button className="pill-button" type="button" onClick={retryLoad}>
                  THỬ LẠI
                </button>
              </article>
            )}
            {!loading && isAuthenticated && !error && fullTests.length === 0 && (
              <article className="surface" style={cardStyle}>
                <div>
                  <span className="free-label">FULL TEST</span>
                  <h3 className="mt-3">Chưa có đề thi sẵn sàng</h3>
                  <p className="mt-3 text-slate-500">
                    Giáo viên chưa xuất bản Full Test hợp lệ hoặc đề chưa đủ câu hỏi cần thiết.
                  </p>
                </div>
              </article>
            )}
            {!loading && isAuthenticated && !error && fullTests.map(test => (
              <article className="surface" style={cardStyle} key={test.testVersionId}>
                <div>
                  <span className="free-label">FULL TEST</span>
                  <h3 className="mt-3">{test.title}</h3>
                  {test.description && <p className="mt-2 text-slate-500">{test.description}</p>}
                  <p className="mt-3 text-slate-500">
                    7 Part · {test.totalQuestions} câu hỏi
                  </p>
                  <p className="mt-2 text-sm">
                    <Clock3 size={15} className="inline" />
                    {' '}{Math.round(test.durationSeconds / 60)} phút · Listening + Reading
                  </p>
                  {test.publishedAt && (
                    <p className="mt-2 text-xs text-slate-500">
                      Xuất bản: {new Date(test.publishedAt).toLocaleDateString('vi-VN')}
                    </p>
                  )}
                </div>
                <Link
                  className="pill-button"
                  to={`/practice?mode=FULL_TEST&testVersionId=${encodeURIComponent(test.testVersionId)}`}
                  state={{ fullTestTitle: test.title }}
                >
                  BẮT ĐẦU <ArrowRight size={15} className="inline" />
                </Link>
              </article>
            ))}
          </div>

          <h2>Luyện tập theo Part</h2>
          <p className="text-slate-500">
            Chọn Part để luyện tập từ ngân hàng câu hỏi Published.
            Số câu thực tế phụ thuộc dữ liệu khả dụng.
          </p>

          <div style={gridStyle}>
            {practiceSets.map(item => (
              <article
                className="surface"
                style={cardStyle}
                key={item.part}
              >
                <div>
                  <span className="free-label">
                    {item.section.toUpperCase()}
                  </span>

                  <h3 className="mt-3">
                    TOEIC Part {item.part} — {item.title}
                  </h3>

                  <p className="mt-3 text-slate-500">
                    <BookOpen size={15} className="inline" />
                    {' '}Part {item.part} · {item.count} câu là gợi ý luyện tập,
                    không phải blueprint Full Test đã duyệt
                  </p>

                  <p className="mt-2 text-sm">
                    <Headphones size={15} className="inline" />
                    {' '}Luyện tập theo ngân hàng câu hỏi
                  </p>
                </div>

                <Link
                  className="pill-button"
                  to={`/practice?mode=PRACTICE&part=${item.part}`}
                >
                  CHI TIẾT <ArrowRight size={15} className="inline" />
                </Link>
              </article>
            ))}
          </div>
        </div>
        <section className="surface p-5" aria-label="Bài mẫu Guest">
          <GuestPracticeNotice /><h2>Bộ câu hỏi mẫu trên trình duyệt</h2>
          <div className="test-catalog-grid">{guestTests.map(test => <article className="test-list-card surface" key={test.id}>
            <h3>{test.title}</h3><TestMetadata test={test} /><Link className="pill-button" to={`/tests/${test.id}/overview`}>XEM CHI TIẾT</Link>
          </article>)}</div>
        </section>
      </div>
    </AssessmentLayout>
  );
}
