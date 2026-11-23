import { useState, useEffect } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import AssessmentLayout from '@/components/templates/AssessmentLayout';
import { remoteAssessmentRepo } from '@/features/assessment/repositories/assessment.repo';
import { Button } from '@/components/ui/button';

export default function PracticeSetupPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [mode, setMode] = useState('PRACTICE');
  const [parts, setParts] = useState([5]);
  const [count, setCount] = useState(30);
  const [minutes, setMinutes] = useState(20);
  const [practiceGoalMinutes, setPracticeGoalMinutes] = useState(30);
  const [fullTestVersionId, setFullTestVersionId] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const requestedMode = params.get('mode');
    const requestedPart = Number(params.get('part'));
    const requestedTestVersionId = params.get('testVersionId');

    if (requestedMode === 'FULL_TEST') {
      setMode('FULL_TEST');
      setFullTestVersionId(requestedTestVersionId || '');
    } else if (
      requestedMode === 'PRACTICE' &&
      Number.isInteger(requestedPart) &&
      requestedPart >= 1 &&
      requestedPart <= 7
    ) {
      setMode('PRACTICE');
      setParts([requestedPart]);
      setCount(requestedPart === 5 ? 30 : 4);
      setPracticeGoalMinutes(30);
    }
  }, [location.search]);

  const isSectionMode = mode === 'SECTION' || mode === 'LISTENING_SECTION';
  const sectionType = mode === 'LISTENING_SECTION' ? 'LISTENING' : 'READING';

  function togglePart(part) {
    setParts(value => (value.includes(part) ? value.filter(p => p !== part) : [...value, part].sort((a, b) => a - b)));
  }

  async function start(event) {
    event.preventDefault();
    if (mode === 'FULL_TEST' && !fullTestVersionId) {
      setError('Vui lòng chọn một đề Full Test đã xuất bản từ thư viện đề thi.');
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
              setCount(nextMode === 'SECTION' || nextMode === 'LISTENING_SECTION' ? 8 : 30);
              setError('');
            }}
          >
            <option value="FULL_TEST">Full Test — 200 câu / 120 phút</option>
            <option value="PRACTICE">Practice theo Part (Part 1–7)</option>
            <option value="LISTENING_SECTION">Listening Section (Part 1–4)</option>
            <option value="SECTION">Reading Section (Part 5–7)</option>
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
          ) : mode === 'FULL_TEST' ? (
            <div className="space-y-3 text-sm text-slate-600">
              <p>
                Full TOEIC Listening & Reading — 200 câu,
                7 Part, thời gian 120 phút.
                Không chọn riêng Part trong chế độ này.
              </p>
              {!fullTestVersionId && (
                <p>
                  Chưa chọn đề đã xuất bản.{' '}
                  <Link to="/tests" className="text-blue-600 underline">
                    Mở thư viện đề thi
                  </Link>
                </p>
              )}
            </div>
          ) : mode === 'LISTENING_SECTION' ? (
            <p>Listening Section gồm đầy đủ Part 1, 2, 3, 4 kèm audio và hình minh họa. Demo có tổ hợp 6–8 câu; nhóm câu không bị cắt.</p>
          ) : (
            <p>Reading gồm đầy đủ Part 5, 6, 7. Demo có tổ hợp 8–11 câu; nhóm câu không bị cắt.</p>
          )}

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
                Đây là cấu hình luyện tập Section ({sectionType}), hạn bài tính theo giờ máy chủ; ở chế độ Section có tùy chọn khóa tua/nghe 1 lần giống phòng thi thật.
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
