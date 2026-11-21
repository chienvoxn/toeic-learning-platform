import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import AssessmentLayout from '@/components/templates/AssessmentLayout';
import { remoteAssessmentRepo } from '@/features/assessment/repositories/assessment.repo';
import { Button } from '@/components/ui/button';

export default function PracticeSetupPage() {
  const navigate = useNavigate();
  const [mode, setMode] = useState('PRACTICE');
  const [sectionType, setSectionType] = useState('READING');
  const [parts, setParts] = useState([5]);
  const [count, setCount] = useState(4);
  const [minutes, setMinutes] = useState(20);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const isSectionMode = mode === 'SECTION';

  function togglePart(part) {
    setParts(value => (value.includes(part) ? value.filter(p => p !== part) : [...value, part].sort((a, b) => a - b)));
  }

  async function start(event) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      const attempt = mode === 'PRACTICE'
        ? await remoteAssessmentRepo.startPractice({ partNumbers: parts, questionCount: Number(count) })
        : await remoteAssessmentRepo.startSection({ type: sectionType, questionCount: Number(count), durationMinutes: Number(minutes) });
      navigate(`/practice/attempts/${attempt.id}`);
    } catch (err) {
      setError(['PRACTICE_QUESTIONS_INSUFFICIENT', 'SECTION_QUESTIONS_INSUFFICIENT'].includes(err.code)
        ? 'Không đủ tổ hợp câu hỏi phù hợp. Hãy đổi số câu hoặc Part; nhóm câu được giữ nguyên.'
        : err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <AssessmentLayout>
      <div className="max-w-3xl mx-auto space-y-5">
        <div className="page-heading">
          <h1>Luyện tập &amp; Section TOEIC (Listening / Reading)</h1>
          <p>Bài làm được lưu trên tài khoản. Audio/hình ảnh lấy qua Pre-signed URL, điểm và transcript/lời giải do máy chủ trả sau khi nộp.</p>
        </div>
        <form className="surface p-6 space-y-5" onSubmit={start}>
          <label className="block font-bold" htmlFor="practice-mode">Chế độ</label>
          <select
            id="practice-mode"
            className="border rounded-lg p-2 w-full"
            value={mode}
            disabled={busy}
            onChange={event => {
              const nextMode = event.target.value;
              setMode(nextMode);
              setCount(nextMode === 'SECTION' ? 8 : 4);
              setError('');
            }}
          >
            <option value="PRACTICE">Practice theo Part (Part 1–7)</option>
            <option value="SECTION">Thi Section (Listening / Reading)</option>
          </select>

          {mode === 'PRACTICE' ? (
            <fieldset className="space-y-3">
              <legend className="font-bold mb-2">Chọn Part</legend>
              <div>
                <span className="text-xs font-semibold uppercase text-slate-500 block mb-1">Listening (Nghe hiểu)</span>
                <div className="flex flex-wrap gap-5">
                  {[1, 2, 3, 4].map(part => (
                    <label key={part} className="flex items-center gap-1.5">
                      <input
                        type="checkbox"
                        checked={parts.includes(part)}
                        disabled={busy}
                        onChange={() => togglePart(part)}
                      />
                      <span>Part {part}</span>
                    </label>
                  ))}
                </div>
              </div>
              <div>
                <span className="text-xs font-semibold uppercase text-slate-500 block mb-1">Reading (Đọc hiểu)</span>
                <div className="flex flex-wrap gap-5">
                  {[5, 6, 7].map(part => (
                    <label key={part} className="flex items-center gap-1.5">
                      <input
                        type="checkbox"
                        checked={parts.includes(part)}
                        disabled={busy}
                        onChange={() => togglePart(part)}
                      />
                      <span>Part {part}</span>
                    </label>
                  ))}
                </div>
              </div>
            </fieldset>
          ) : (
            <>
              <label className="block font-bold" htmlFor="section-type">Kỹ năng Section</label>
              <select id="section-type" className="border rounded-lg p-2 w-full" value={sectionType} disabled={busy} onChange={event => setSectionType(event.target.value)}>
                <option value="READING">Reading · Part 5–7</option>
                <option value="LISTENING">Listening · Part 1–4</option>
              </select>
              <p>{sectionType === 'LISTENING' ? 'Listening gồm đầy đủ Part 1, 2, 3, 4.' : 'Reading gồm đầy đủ Part 5, 6, 7.'} Mỗi Part có ít nhất một câu; nhóm câu không bị cắt. Cần đủ nguồn đã xuất bản cho tổng số câu đã chọn.</p>
            </>
          )}

          <label className="block font-bold" htmlFor="practice-count">Số câu</label>
          <input
            id="practice-count"
            className="border rounded-lg p-2"
            type="number"
            min={1}
            max={isSectionMode ? 100 : 200}
            required
            value={count}
            disabled={busy}
            onChange={event => setCount(event.target.value)}
          />

          {isSectionMode && (
            <>
              <label className="block font-bold" htmlFor="practice-minutes">Thời lượng (phút)</label>
              <input
                id="practice-minutes"
                className="border rounded-lg p-2"
                type="number"
                min={1}
                max={120}
                required
                value={minutes}
                disabled={busy}
                onChange={event => setMinutes(event.target.value)}
              />
              <p className="text-sm text-slate-500">
                Đây là cấu hình luyện tập Section ({sectionType}), không phải thời lượng/cấu trúc TOEIC chính thức. Hạn bài tính theo giờ máy chủ; chưa tự chốt khi không có request. Khóa tua/nghe một lần là tùy chọn luyện tập trên giao diện.
              </p>
            </>
          )}

          {mode === 'PRACTICE' && <p className="text-sm text-slate-500">Phiên Practice có hạn 24 giờ theo máy chủ.</p>}
          {error && <p className="form-error" role="alert">{error}</p>}
          <Button type="submit" disabled={busy || (mode === 'PRACTICE' && !parts.length)}>
            {busy ? 'Đang tạo phiên...' : 'Bắt đầu làm bài thật'}
          </Button>
        </form>
        <p className="text-sm text-slate-500">Transcript và đáp án đúng được bảo mật trong khi làm bài và chỉ hiển thị sau khi nộp bài.</p>
        <Link to="/tests" className="pill-button">Mở bài mẫu Guest — chỉ lưu trên trình duyệt</Link>
      </div>
    </AssessmentLayout>
  );
}
