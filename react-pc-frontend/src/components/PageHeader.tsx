import React from 'react';

interface PageHeaderProps {
    /** Small category label above the title (appears in rose-600) */
    category: string;
    /** Main page title (uppercase, bold) */
    title: string;
    /** Description text below the title */
    description: string;
    /** Action buttons to display on the right side */
    actions?: React.ReactNode;
}

/**
 * Standardized page header component for all React pages.
 * Uses consistent styling with rose category label, bold title, and description.
 * 
 * @example
 * <PageHeader
 *   category="Buchhaltung"
 *   title="OFFENE POSTEN"
 *   description="Übersicht aller unbezahlten Rechnungen und Mahnungen."
 *   actions={
 *     <Button>Aktion</Button>
 *   }
 * />
 */
export function PageHeader({ category, title, description, actions }: PageHeaderProps) {
    return (
        <div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
            <div>
                <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">{category}</p>
                <h1 className="text-3xl font-bold text-slate-900">{title}</h1>
                <p className="text-slate-500 mt-1">{description}</p>
            </div>
            {actions && (
                <div className="flex gap-2 flex-shrink-0">
                    {actions}
                </div>
            )}
        </div>
    );
}

export default PageHeader;
