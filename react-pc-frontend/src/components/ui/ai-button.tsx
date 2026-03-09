import React from 'react';
import { Gem } from 'lucide-react';
import { Button } from './button';
import { cn } from '../../lib/utils';

interface AiButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
    isLoading?: boolean;
    label?: string;
}

export function AiButton({ isLoading, label = "KI-Optimierung", className, disabled, ...props }: AiButtonProps) {
    return (
        <Button
            variant="ghost"
            size="sm"
            disabled={disabled || isLoading}
            className={cn("text-rose-600 hover:bg-rose-100 group transition-all", className)}
            {...props}
        >
            {isLoading ? (
                <div className="relative w-4 h-4 mr-2">
                    <Gem className="w-4 h-4 text-rose-600 animate-pulse" />
                    <div className="absolute inset-0 bg-rose-400 rounded-full blur-sm opacity-50 animate-ping"></div>
                </div>
            ) : (
                <Gem className="w-4 h-4 mr-2 group-hover:drop-shadow-sm transition-all" />
            )}
            {label}
        </Button>
    );
}
