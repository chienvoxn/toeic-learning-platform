import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { remoteAssessmentRepo } from './repositories/assessment.repo';

export default function FullMockCatalog() {
  const [rows, setRows] = useState(null), [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    remoteAssessmentRepo.getCatalog().then(value => { if (active) setRows(value); }).catch(e => { if (active) setError(e.message); });
    return () => { active = false; };
  }, []);
  return <section className="surface p-5 mb-5" aria-label="Full Mock trên máy chủ">
    <h2>Full Mock lưu trên tài khoản</h2>
    <p>Listening và Reading, Part 1–7 theo blueprint của từng phiên bản. Thời hạn và kết quả được lưu trên máy chủ.</p>
    {error && <p role="alert">{error}</p>}
    {!rows && !error && <p>Đang tải bộ đề…</p>}
    {rows?.length === 0 && <p>Chưa có bộ đề được cung cấp.</p>}
    <ul>{rows?.map(row => <li key={row.versionId}><Link to={`/tests/${row.testId}/overview?server=1`}>{row.title} · v{row.version} · {row.totalQuestions} câu · {row.durationSeconds / 60} phút</Link></li>)}</ul>
  </section>;
}
