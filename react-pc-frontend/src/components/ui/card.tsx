import { cn } from '../../lib/utils';
import React from 'react';

export const Card: React.FC<React.HTMLAttributes<HTMLDivElement>> = ({ className, ...props }) => (
  <div className={cn('bg-white border border-slate-200 rounded-lg shadow-sm', className)} {...props} />
);
