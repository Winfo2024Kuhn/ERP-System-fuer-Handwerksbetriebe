import React, { useState, useRef, useEffect, useMemo } from 'react';
import { Users, ChevronDown, ChevronUp, Copy, Check } from 'lucide-react';
import { cn } from '../lib/utils';

import { parseRecipientList, type ParsedEmailRecipient } from '../lib/emailAddress';
export { parseRecipientList, type ParsedEmailRecipient };

interface EmailRecipientDropdownProps {
    recipients?: string;
    cc?: string;
    label?: string;
    className?: string;
    maxInline?: number;
    showCopyAll?: boolean;
}

export function EmailRecipientDropdown({
    recipients,
    cc,
    label = 'An:',
    className,
    maxInline = 2,
    showCopyAll = true,
}: EmailRecipientDropdownProps) {
    const [isOpen, setIsOpen] = useState(false);
    const [copiedAll, setCopiedAll] = useState(false);
    const [copiedIndex, setCopiedIndex] = useState<number | null>(null);
    const [searchQuery, setSearchQuery] = useState('');
    const dropdownRef = useRef<HTMLDivElement>(null);

    const toList = useMemo(() => parseRecipientList(recipients), [recipients]);
    const ccList = useMemo(() => parseRecipientList(cc), [cc]);
    const totalCount = toList.length + ccList.length;

    // Klick außerhalb schließt das Dropdown
    useEffect(() => {
        if (!isOpen) return;

        const handleClickOutside = (e: MouseEvent) => {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
                setIsOpen(false);
            }
        };
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'Escape') {
                setIsOpen(false);
            }
        };

        document.addEventListener('mousedown', handleClickOutside);
        document.addEventListener('keydown', handleKeyDown);
        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
            document.removeEventListener('keydown', handleKeyDown);
        };
    }, [isOpen]);

    if (totalCount === 0) {
        return null;
    }

    const inlineTo = toList.slice(0, maxInline);
    const remainingCount = totalCount - inlineTo.length;

    const handleCopyAll = (e: React.MouseEvent) => {
        e.stopPropagation();
        const allEmails = [...toList, ...ccList].map(r => r.email).join(', ');
        navigator.clipboard.writeText(allEmails);
        setCopiedAll(true);
        setTimeout(() => setCopiedAll(false), 2000);
    };

    const handleCopySingle = (email: string, index: number, e: React.MouseEvent) => {
        e.stopPropagation();
        navigator.clipboard.writeText(email);
        setCopiedIndex(index);
        setTimeout(() => setCopiedIndex(null), 1500);
    };

    const filteredToList = searchQuery.trim()
        ? toList.filter(r => r.displayName.toLowerCase().includes(searchQuery.toLowerCase()) || r.email.toLowerCase().includes(searchQuery.toLowerCase()))
        : toList;

    const filteredCcList = searchQuery.trim()
        ? ccList.filter(r => r.displayName.toLowerCase().includes(searchQuery.toLowerCase()) || r.email.toLowerCase().includes(searchQuery.toLowerCase()))
        : ccList;

    return (
        <div className={cn('relative inline-flex items-center flex-wrap gap-1.5 text-xs', className)} ref={dropdownRef}>
            {label && <span className="text-slate-400 select-none font-medium shrink-0">{label}</span>}

            {/* Inline sichtbare Empfänger */}
            <div className="inline-flex items-center flex-wrap gap-1.5 max-w-full">
                {inlineTo.map((r, idx) => {
                    const isNameDifferent = r.displayName && r.displayName.toLowerCase() !== r.email.toLowerCase();
                    return (
                        <span key={idx} className="inline-flex items-baseline text-slate-700 max-w-full truncate">
                            <span className="font-medium text-slate-800 truncate">{r.displayName}</span>
                            {isNameDifferent && (
                                <span className="text-slate-400 font-normal ml-1 truncate">&lt;{r.email}&gt;</span>
                            )}
                            {idx < inlineTo.length - 1 && (
                                <span className="text-slate-400 mr-1">,</span>
                            )}
                        </span>
                    );
                })}

                {/* Badge für weitere Empfänger (z.B. Rundmail) */}
                {remainingCount > 0 && (
                    <button
                        type="button"
                        onClick={() => setIsOpen(!isOpen)}
                        aria-expanded={isOpen}
                        className={cn(
                            'inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold cursor-pointer select-none transition-all shadow-xs',
                            isOpen
                                ? 'bg-rose-100 text-rose-800 ring-1 ring-rose-300'
                                : 'bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-300 hover:border-slate-400'
                        )}
                        title={`${remainingCount} weitere Empfänger anzeigen`}
                    >
                        <Users className="w-3 h-3 text-slate-500" />
                        <span>+{remainingCount} weitere</span>
                        {isOpen ? <ChevronUp className="w-3 h-3 text-slate-400" /> : <ChevronDown className="w-3 h-3 text-slate-400" />}
                    </button>
                )}
            </div>

            {/* Dropdown / Popover mit vollständiger Empfängerliste */}
            {isOpen && (
                <div
                    className="absolute left-0 top-full mt-1.5 z-50 w-80 sm:w-96 max-w-[90vw] bg-white rounded-xl shadow-xl border border-slate-200 flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-100"
                    style={{ maxHeight: '360px' }}
                >
                    {/* Header */}
                    <div className="px-3.5 py-2.5 bg-slate-50 border-b border-slate-200 flex items-center justify-between gap-2 shrink-0">
                        <div className="flex items-center gap-1.5">
                            <Users className="w-4 h-4 text-slate-500" />
                            <span className="font-bold text-xs text-slate-800">
                                Alle Empfänger ({totalCount})
                            </span>
                        </div>
                        {showCopyAll && (
                            <button
                                type="button"
                                onClick={handleCopyAll}
                                className="inline-flex items-center gap-1 px-2 py-1 rounded text-[11px] font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-200/70 transition-colors cursor-pointer"
                                title="Alle E-Mail-Adressen in die Zwischenablage kopieren"
                            >
                                {copiedAll ? (
                                    <>
                                        <Check className="w-3 h-3 text-emerald-600" />
                                        <span className="text-emerald-700 font-semibold">Kopiert!</span>
                                    </>
                                ) : (
                                    <>
                                        <Copy className="w-3 h-3 text-slate-400" />
                                        <span>Alle kopieren</span>
                                    </>
                                )}
                            </button>
                        )}
                    </div>

                    {/* Suchfilter bei vielen Empfängern */}
                    {totalCount > 6 && (
                        <div className="p-2 border-b border-slate-100 bg-white shrink-0">
                            <input
                                type="text"
                                placeholder="Empfänger filtern..."
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                className="w-full px-2.5 py-1 text-xs rounded border border-slate-200 focus:outline-none focus:border-rose-400 focus:ring-1 focus:ring-rose-200"
                            />
                        </div>
                    )}

                    {/* Empfänger-Liste scrollbar */}
                    <div className="overflow-y-auto flex-1 p-2 space-y-1 divide-y divide-slate-100">
                        {filteredToList.length > 0 && (
                            <div>
                                {ccList.length > 0 && (
                                    <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider px-2 py-1">
                                        An ({filteredToList.length})
                                    </p>
                                )}
                                {filteredToList.map((r, idx) => {
                                    const isNameDifferent = r.displayName && r.displayName.toLowerCase() !== r.email.toLowerCase();
                                    return (
                                        <div
                                            key={`to-${idx}`}
                                            className="group flex items-center justify-between gap-2 px-2.5 py-1.5 rounded-lg hover:bg-slate-50 transition-colors"
                                        >
                                            <div className="flex items-center gap-2 min-w-0 flex-1">
                                                <div className="w-6 h-6 rounded-full bg-rose-100 text-rose-700 font-bold text-[10px] flex items-center justify-center shrink-0">
                                                    {(r.displayName.charAt(0) || '?').toUpperCase()}
                                                </div>
                                                <div className="min-w-0 flex-1">
                                                    <p className="text-xs font-medium text-slate-800 truncate">
                                                        {r.displayName}
                                                    </p>
                                                    {isNameDifferent && (
                                                        <p className="text-[11px] text-slate-400 truncate">
                                                            {r.email}
                                                        </p>
                                                    )}
                                                </div>
                                            </div>
                                            <button
                                                type="button"
                                                onClick={(e) => handleCopySingle(r.email, idx, e)}
                                                className="opacity-0 group-hover:opacity-100 p-1 rounded hover:bg-slate-200/60 text-slate-400 hover:text-slate-700 transition-all shrink-0 cursor-pointer"
                                                title="Adresse kopieren"
                                            >
                                                {copiedIndex === idx ? (
                                                    <Check className="w-3 h-3 text-emerald-600" />
                                                ) : (
                                                    <Copy className="w-3 h-3" />
                                                )}
                                            </button>
                                        </div>
                                    );
                                })}
                            </div>
                        )}

                        {filteredCcList.length > 0 && (
                            <div className="pt-1">
                                <p className="text-[10px] font-bold text-slate-400 uppercase tracking-wider px-2 py-1">
                                    Kopie (CC) ({filteredCcList.length})
                                </p>
                                {filteredCcList.map((r, idx) => {
                                    const isNameDifferent = r.displayName && r.displayName.toLowerCase() !== r.email.toLowerCase();
                                    return (
                                        <div
                                            key={`cc-${idx}`}
                                            className="group flex items-center justify-between gap-2 px-2.5 py-1.5 rounded-lg hover:bg-slate-50 transition-colors"
                                        >
                                            <div className="flex items-center gap-2 min-w-0 flex-1">
                                                <div className="w-6 h-6 rounded-full bg-slate-100 text-slate-600 font-bold text-[10px] flex items-center justify-center shrink-0">
                                                    {(r.displayName.charAt(0) || '?').toUpperCase()}
                                                </div>
                                                <div className="min-w-0 flex-1">
                                                    <p className="text-xs font-medium text-slate-800 truncate">
                                                        {r.displayName}
                                                    </p>
                                                    {isNameDifferent && (
                                                        <p className="text-[11px] text-slate-400 truncate">
                                                            {r.email}
                                                        </p>
                                                    )}
                                                </div>
                                            </div>
                                            <button
                                                type="button"
                                                onClick={(e) => handleCopySingle(r.email, 1000 + idx, e)}
                                                className="opacity-0 group-hover:opacity-100 p-1 rounded hover:bg-slate-200/60 text-slate-400 hover:text-slate-700 transition-all shrink-0 cursor-pointer"
                                                title="Adresse kopieren"
                                            >
                                                {copiedIndex === 1000 + idx ? (
                                                    <Check className="w-3 h-3 text-emerald-600" />
                                                ) : (
                                                    <Copy className="w-3 h-3" />
                                                )}
                                            </button>
                                        </div>
                                    );
                                })}
                            </div>
                        )}

                        {filteredToList.length === 0 && filteredCcList.length === 0 && (
                            <div className="py-4 text-center text-xs text-slate-400">
                                Keine passenden Empfänger gefunden.
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
