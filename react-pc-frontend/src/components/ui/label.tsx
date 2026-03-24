import { cn } from '../../lib/utils';
import React from 'react';

export type LabelProps = React.LabelHTMLAttributes<HTMLLabelElement>;

export const Label: React.FC<LabelProps> = ({ className, ...props }) => (
  <label className={cn('block text-sm text-slate-800 mb-1.5', className)} {...props} />
);
