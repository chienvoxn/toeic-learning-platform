import { useState, useEffect } from 'react';
import { Clock } from 'lucide-react';
import { cn } from '@/lib/utils';

export function CountdownTimer({ expiresAt, onExpire, className }) {
  const [timeLeftSeconds, setTimeLeftSeconds] = useState(() => {
    if (!expiresAt) return 0;
    return Math.max(0, Math.floor((expiresAt - Date.now()) / 1000));
  });

  useEffect(() => {
    if (!expiresAt) return;

    const interval = setInterval(() => {
      const remaining = Math.max(0, Math.floor((expiresAt - Date.now()) / 1000));
      setTimeLeftSeconds(remaining);

      if (remaining <= 0) {
        clearInterval(interval);
        if (onExpire) onExpire();
      }
    }, 1000);

    return () => clearInterval(interval);
  }, [expiresAt, onExpire]);

  const hours = Math.floor(timeLeftSeconds / 3600);
  const minutes = Math.floor((timeLeftSeconds % 3600) / 60);
  const seconds = timeLeftSeconds % 60;

  const formatted = hours > 0
    ? `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
    : `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;

  const isUrgent = timeLeftSeconds < 300 && timeLeftSeconds > 0;

  return (
    <div
      className={cn(
        'inline-flex items-center gap-2 px-3 py-1.5 rounded-lg font-mono text-sm font-semibold transition-colors',
        isUrgent
          ? 'bg-red-50 text-red-600 border border-red-200 animate-pulse'
          : 'bg-slate-100 text-slate-800 border border-slate-200',
        className
      )}
    >
      <Clock className={cn('h-4 w-4', isUrgent ? 'text-red-500' : 'text-slate-500')} />
      <span>{formatted}</span>
    </div>
  );
}
