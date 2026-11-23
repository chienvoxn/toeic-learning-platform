import { Link } from 'react-router-dom';
import { BookOpen, Clock3, Headphones, ArrowRight } from 'lucide-react';
import AssessmentLayout from '@/components/templates/AssessmentLayout';

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
            <article className="surface" style={cardStyle}>
              <div>
                <span className="free-label">FULL TEST</span>
                <h3 className="mt-3">
                  ENGLINGO TOEIC Listening & Reading — Test 01
                </h3>
                <p className="mt-3 text-slate-500">
                  7 Part · 200 câu hỏi
                </p>
                <p className="mt-2 text-sm">
                  <Clock3 size={15} className="inline" />
                  {' '}120 phút · Listening + Reading
                </p>
              </div>
              <Link
                className="pill-button"
                to="/practice?mode=FULL_TEST"
              >
                CHI TIẾT <ArrowRight size={15} className="inline" />
              </Link>
            </article>
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
                    {' '}Part {item.part} · {item.count} câu
                    trong cấu trúc Full Test
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
      </div>
    </AssessmentLayout>
  );
}
