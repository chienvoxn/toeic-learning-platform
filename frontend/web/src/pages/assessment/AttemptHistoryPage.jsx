import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import AssessmentLayout from '@/components/templates/AssessmentLayout';
import { remoteAssessmentRepo } from '@/features/assessment/repositories/assessment.repo';
import { Button } from '@/components/ui/button';

const sourceLabels = { PRACTICE_SNAPSHOT: 'Practice', SECTION_SNAPSHOT: 'Section', TEST_VERSION: 'Bộ đề' };
const time = value => new Date(value).toLocaleString('vi-VN');

export default function AttemptHistoryPage() {
  const [page, setPage] = useState(0);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let active = true;
    remoteAssessmentRepo.listAttempts({ page, size: 20 })
      .then(value => { if (active) setData(value); })
      .catch(err => { if (active) setError(err.message); });
    return () => { active = false; };
  }, [page, refresh]);
  function load(next) { setData(null); setError(''); setPage(next); setRefresh(value => value + 1); }
  return <AssessmentLayout>
    <div className="max-w-4xl mx-auto space-y-5">
      <div className="page-heading"><h1>Lịch sử bài làm</h1><p>Chỉ bài làm của bạn. Điểm thô đọc từ kết quả đã lưu, chưa quy đổi điểm TOEIC.</p></div>
      <div className="flex gap-3"><Link className="pill-button navy" to="/practice">Làm bài mới</Link><Button variant="outline" onClick={() => load(page)}>Tải lại</Button></div>
      {error ? <div role="alert">{error}</div> : !data ? <p role="status">Đang tải lịch sử…</p> : <>
        {data.items.length === 0 && <p>Chưa có bài làm trong trang này.</p>}
        <ul className="space-y-3" aria-label="Các bài làm">
          {data.items.map(item => <li key={item.attemptId} className="surface p-5 space-y-2" data-attempt-id={item.attemptId}>
            <h2>{sourceLabels[item.sourceType] || 'Bài làm'} · {item.totalQuestions} câu</h2>
            <p>Bắt đầu: {time(item.startedAt)} · Hạn: {time(item.expiresAt)}</p>
            {item.status === 'GRADED' ? <>
              <p>Đã chấm · Nộp: {time(item.submittedAt)}</p>
              {item.score && <p>Điểm thô: {item.score.rawScore}/{item.totalQuestions} · Đúng {item.score.correctCount} · Sai {item.score.incorrectCount} · Bỏ trống {item.score.unansweredCount} · Accuracy {item.score.accuracy}%</p>}
              <Link className="underline" to={`/practice/attempts/${item.attemptId}/result`}>Xem kết quả</Link>
            </> : <><p>Chưa chấm — quyền tiếp tục và thời hạn do máy chủ kiểm tra.</p><Link className="underline" to={`/practice/attempts/${item.attemptId}`}>Mở bài làm</Link></>}
          </li>)}
        </ul>
        <nav className="flex items-center gap-3" aria-label="Phân trang lịch sử">
          <Button variant="outline" disabled={page === 0} onClick={() => load(page - 1)}>Trang trước</Button>
          <span>Trang {page + 1} / {Math.max(1, data.totalPages)} · {data.totalElements} bài</span>
          <Button variant="outline" disabled={page + 1 >= data.totalPages} onClick={() => load(page + 1)}>Trang sau</Button>
        </nav>
      </>}
    </div>
  </AssessmentLayout>;
}
