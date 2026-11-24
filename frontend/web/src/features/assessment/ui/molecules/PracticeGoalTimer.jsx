import { useEffect, useState } from 'react';

export function PracticeGoalTimer({ startedAt, minutes }) {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  const start = Date.parse(startedAt);
  if (!Number.isFinite(start)) return null;

  const goal = start + minutes * 60 * 1000;
  const remaining = Math.max(0, Math.ceil((goal - now) / 1000));

  const mm = String(Math.floor(remaining / 60)).padStart(2, '0');
  const ss = String(remaining % 60).padStart(2, '0');

  return (
    <div className="flex flex-col items-center text-sm">
      <strong>{remaining > 0 ? `${mm}:${ss}` : '00:00'}</strong>
      <span className="text-xs">
        {remaining > 0
          ? `Mục tiêu ${minutes} phút`
          : 'Đã hết thời gian mục tiêu — vẫn có thể nộp bài'}
      </span>
    </div>
  );
}
