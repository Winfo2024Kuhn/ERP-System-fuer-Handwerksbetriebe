import { cn } from '../../lib/utils';
import React from 'react';

type Variant = 'default' | 'outline' | 'ghost' | 'secondary';
type Size = 'default' | 'sm';

const variantClasses: Record<Variant, string> = {
  default: 'bg-rose-600 text-white hover:bg-rose-700 border border-rose-600',
  outline: 'bg-white text-rose-700 border border-rose-200 hover:bg-rose-50',
  ghost: 'bg-transparent text-rose-700 hover:bg-rose-100',
  secondary: 'bg-slate-100 text-slate-800 border border-slate-200 hover:bg-slate-200'
};

const sizeClasses: Record<Size, string> = {
  default: 'px-4 py-2 text-sm rounded-lg',
  sm: 'px-3 py-1.5 text-sm rounded'
};

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
}

export const Button: React.FC<ButtonProps> = ({ className, variant = 'default', size = 'default', ...props }) => {
  return (
    <button
      className={cn(
        'inline-flex items-center justify-center gap-2 transition-colors disabled:opacity-50 disabled:cursor-not-allowed',
        variantClasses[variant],
        sizeClasses[size],
        className
      )}
      {...props}
    />
  );
};
