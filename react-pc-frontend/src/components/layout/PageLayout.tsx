import React from 'react';
import { cn } from '../../lib/utils';

interface PageLayoutProps {
    /** The category label displayed in small red text above the title (e.g. "Stammdaten") */
    ribbonCategory?: string;
    /** The main title of the page (e.g. "Lieferantenübersicht") */
    title?: string;
    /** Optional subtitle or description text */
    subtitle?: string;
    /** Optional action buttons displayed on the right side of the header */
    actions?: React.ReactNode;
    /** The content of the page */
    children: React.ReactNode;
    /** Optional className for the wrapper */
    className?: string;
}

/**
 * Standardized page layout component for all pages.
 * enforce consistent padding, centering, and header styling.
 */
export const PageLayout: React.FC<PageLayoutProps> = ({
    ribbonCategory,
    title,
    subtitle,
    actions,
    children,
    className
}) => {
    return (
        <div className={cn("w-full max-w-[1600px] mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6", className)}>
            {/* Standardized Header - only render if title is present */}
            {title && (
                <div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8 border-b border-transparent">
                    <div>
                        {ribbonCategory && (
                            <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">
                                {ribbonCategory}
                            </p>
                        )}
                        <h1 className="text-3xl font-bold text-slate-900 uppercase">
                            {title}
                        </h1>
                        {subtitle && (
                            <p className="text-slate-500 mt-1">
                                {subtitle}
                            </p>
                        )}
                    </div>
                    {actions && (
                        <div className="flex gap-2 flex-shrink-0">
                            {actions}
                        </div>
                    )}
                </div>
            )}

            {/* Page Content */}
            {children}
        </div>
    );
};
