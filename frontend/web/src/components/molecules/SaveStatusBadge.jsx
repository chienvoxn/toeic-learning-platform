import { CheckCircle2, Clock, AlertCircle } from 'lucide-react';
import { cn } from '@/lib/utils';

export function SaveStatusBadge({ status, onRetry, className, retryDisabled = false }) {
  if (status === 'saving') {
    return (
      <span className={cn('inline-flex items-center gap-1.5 text-xs text-amber-600 font-medium animate-pulse', className)}>
        <Clock className="h-3.5 w-3.5" />
        Đang lưu...
      </span>
    );
  }

  if (status === 'error') {
    return (
      <button
        onClick={onRetry}
        disabled={retryDisabled}
        className={cn('inline-flex items-center gap-1.5 text-xs text-red-600 font-medium hover:underline cursor-pointer', className)}
      >
        <AlertCircle className="h-3.5 w-3.5" />
        {retryDisabled ? 'Có lựa chọn chưa xác nhận đã lưu' : 'Chưa lưu được (Nhấn thử lại)'}
      </button>
    );
  }

  return (
    <span className={cn('inline-flex items-center gap-1.5 text-xs text-emerald-600 font-medium', className)}>
      <CheckCircle2 className="h-3.5 w-3.5" />
      Đã lưu
    </span>
  );
}
