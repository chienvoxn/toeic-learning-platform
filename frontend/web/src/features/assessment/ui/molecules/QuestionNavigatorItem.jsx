import { Flag } from 'lucide-react';
import { cn } from '@/lib/utils';

export function QuestionNavigatorItem({
  orderIndex,
  isAnswered,
  isCurrent,
  isFlagged,
  onClick,
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'relative flex h-10 w-10 items-center justify-center rounded-lg text-xs font-semibold transition-all cursor-pointer border',
        isCurrent
          ? 'border-blue-600 bg-blue-600 text-white shadow-sm ring-2 ring-blue-600/30'
          : isAnswered
          ? 'border-blue-200 bg-blue-50 text-blue-700 hover:bg-blue-100'
          : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
      )}
    >
      {orderIndex}
      {isFlagged && (
        <span className="absolute -top-1 -right-1 flex h-3.5 w-3.5 items-center justify-center rounded-full bg-amber-500 text-white">
          <Flag className="h-2 w-2 fill-current" />
        </span>
      )}
    </button>
  );
}
