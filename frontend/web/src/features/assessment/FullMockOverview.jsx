import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { remoteAssessmentRepo } from './repositories/assessment.repo';
import AssessmentLayout from '@/components/templates/AssessmentLayout';

export default function FullMockOverview({ testId }) {
  const navigate = useNavigate();
  const [test, setTest] = useState(null), [error, setError] = useState(''), [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    remoteAssessmentRepo.getCatalog().then(rows => {
      if (!active) return;
      const value = rows.find(row => row.testId === testId);
      if (value) setTest(value); else setError('Bộ đề không còn được cung cấp.');
    }).catch(e => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [testId]);
  async function start() {
    setBusy(true); setError('');
    try { const a = await remoteAssessmentRepo.startFullMock(test.versionId); navigate(`/practice/attempts/${a.id}`); }
    catch(e) { setError(`${e.message}${e.code ? ` (${e.code})` : ''}`); setBusy(false); }
  }
  return <AssessmentLayout variant="catalog"><div className="space-y-4">
    <Link to="/tests">Bộ đề</Link>
    {error && <p role="alert">{error}</p>}
    {test && <><h1>{test.title} · v{test.version}</h1><p>{test.description}</p>
      <p>{test.totalQuestions} câu · {test.durationSeconds / 60} phút · {test.blueprintCode}/v{test.blueprintVersion}</p>
      <p>Listening: Part 1–4. Reading: Part 5–7. Làm toàn bộ đề theo thứ tự đã pin.</p>
      <p>Đáp án lưu vào tài khoản; mở lại cùng URL để tiếp tục. Hết hạn sẽ khóa sửa và máy chủ tự chốt Full Mock kể cả khi đã đóng trình duyệt. Chỉ đáp án đã được máy chủ lưu trước hạn được chấm. Không quy đổi điểm TOEIC.</p>
      <button className="pill-button" disabled={busy} onClick={start}>{busy ? 'Đang tạo phiên…' : 'Bắt đầu Full Mock'}</button>
    </>}
  </div></AssessmentLayout>;
}
