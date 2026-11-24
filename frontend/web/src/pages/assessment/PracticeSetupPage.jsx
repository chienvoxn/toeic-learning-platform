import { useState, useEffect } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import AssessmentLayout from '@/components/templates/AssessmentLayout';
import { remoteAssessmentRepo } from '@/features/assessment/repositories/assessment.repo';
import { Button } from '@/components/ui/button';

export default function PracticeSetupPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [mode, setMode] = useState('PRACTICE');
  const [sectionType, setSectionType] = useState('READING');
  const [parts, setParts] = useState([5]);
  const [count, setCount] = useState(4);
  const [minutes, setMinutes] = useState(20);
  const [practiceGoalMinutes, setPracticeGoalMinutes] = useState(30);
  const [fullTestVersionId, setFullTestVersionId] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    if (params.get('mode') === 'FULL_TEST') { setMode('FULL_TEST'); setFullTestVersionId(params.get('testVersionId') || ''); }
    else if (['SECTION', 'LISTENING_SECTION'].includes(params.get('mode'))) {
      setMode('SECTION'); setSectionType(params.get('mode') === 'LISTENING_SECTION' ? 'LISTENING' : 'READING'); setCount(8);
    } else if (params.get('mode') === 'PRACTICE') {
      const part = Number(params.get('part'));
      if (Number.isInteger(part) && part >= 1 && part <= 7) { setMode('PRACTICE'); setParts([part]); setCount(part === 5 ? 30 : 4); }
    }
  }, [location.search]);
  const isSectionMode = mode === 'SECTION';
  const fullTestTitle = location.state?.fullTestTitle || 'Full Test đã chọn';


  function togglePart(part) {
    setParts(value => (value.includes(part) ? value.filter(p => p !== part) : [...value, part].sort((a, b) => a - b)));
  }

  async function start(event) {
    event.preventDefault();
    if (mode === 'FULL_TEST' && !fullTestVersionId) {
      setError('Vui lòng chọn một đề Full Test đã xuất bản từ thư viện đề thi.');
      return;
    }
    if (mode === 'FULL_TEST' && !window.confirm(`Bắt đầu làm “${fullTestTitle}”? Thời gian sẽ tính theo máy chủ ngay sau khi tạo phiên.`)) {
      return;
    }
    setBusy(true);
    setError('');
    try {
      const attempt = mode === 'FULL_TEST'
        ? await remoteAssessmentRepo.startFullTest(fullTestVersionId)
        : mode === 'PRACTICE'
          ? await remoteAssessmentRepo.startPractice({
              partNumbers: parts,
              questionCount: Number(count)
            })
          : await remoteAssessmentRepo.startSection({
              type: sectionType,
              questionCount: Number(count),
              durationMinutes: Number(minutes)
            });
      if (mode === 'PRACTICE') {
        localStorage.setItem(
          `englingo_practice_goal_${attempt.id}`,
          String(practiceGoalMinutes)
        );
      }
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
          <h1>Chi tiết &amp; cấu hình bài thi TOEIC</h1>
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
            <option value="FULL_TEST">Full Test — theo phiên bản/blueprint đã xuất bản</option>
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
          ) : isSectionMode ? (
            <>
              <label className="block font-bold" htmlFor="section-type">Kỹ năng Section</label>
              <select id="section-type" className="border rounded-lg p-2 w-full" value={sectionType} disabled={busy} onChange={event => setSectionType(event.target.value)}>
                <option value="READING">Reading · Part 5–7</option>
                <option value="LISTENING">Listening · Part 1–4</option>
              </select>
              <p>{sectionType === 'LISTENING' ? 'Listening gồm đầy đủ Part 1, 2, 3, 4.' : 'Reading gồm đầy đủ Part 5, 6, 7.'} Mỗi Part có ít nhất một câu; nhóm câu không bị cắt. Cần đủ nguồn đã xuất bản cho tổng số câu đã chọn.</p>
            </>
          ) : <p>Full Test dùng thứ tự, số câu và thời hạn từ phiên bản/blueprint trên máy chủ. Không chọn cấu hình Section tại đây.</p>}

          {mode !== 'FULL_TEST' && (
            <>
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

            </>
          )}

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

          {mode === 'PRACTICE' && (
            <div className="space-y-3">
              <label
                className="block font-bold"
                htmlFor="practice-goal-minutes"
              >
                Mục tiêu thời gian luyện tập
              </label>
              <select
                id="practice-goal-minutes"
                className="border rounded-lg p-2 w-full"
                value={practiceGoalMinutes}
                disabled={busy}
                onChange={e =>
                  setPracticeGoalMinutes(Number(e.target.value))
                }
              >
                <option value={15}>15 phút</option>
                <option value={30}>30 phút</option>
                <option value={45}>45 phút</option>
                <option value={60}>60 phút</option>
              </select>
              <p className="text-sm text-slate-500">
                Đồng hồ mục tiêu hiển thị trên giao diện.
                Backend hiện vẫn cho phép làm Practice tối đa 24 giờ;
                hết thời gian mục tiêu không tự động nộp bài.
              </p>
            </div>
          )}
          {error && <p className="form-error" role="alert">{error}</p>}
          <Button type="submit" disabled={busy || (mode === 'PRACTICE' && !parts.length) || (mode === 'FULL_TEST' && !fullTestVersionId)}>
            {busy ? 'Đang tạo phiên...' : 'Bắt đầu làm bài thật'}
          </Button>
        </form>
        <p className="text-sm text-slate-500">Transcript và đáp án đúng được bảo mật trong khi làm bài và chỉ hiển thị sau khi nộp bài.</p>
        <Link to="/tests" className="pill-button">Mở bài mẫu Guest — chỉ lưu trên trình duyệt</Link>
      </div>
    </AssessmentLayout>
  );
}
