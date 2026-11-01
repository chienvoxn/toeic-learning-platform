import * as React from 'react';
import { cn } from '@/lib/utils';

export function Badge({ className, variant = 'default', children, ...props }) {
  const variants = {
    default: 'bg-blue-600 text-white',
    secondary: 'bg-slate-100 text-slate-800',
    success: 'bg-emerald-100 text-emerald-800 border-emerald-200',
    warning: 'bg-amber-100 text-amber-800 border-amber-200',
    destructive: 'bg-red-100 text-red-800 border-red-200',
    outline: 'border border-slate-300 text-slate-700 bg-white',
  };

  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium border border-transparent transition-colors',
        variants[variant],
        className
      )}
      {...props}
    >
      {children}
    </span>
  );
}
