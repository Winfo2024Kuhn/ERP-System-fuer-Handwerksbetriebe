import { useState, useMemo, useEffect, useRef } from 'react';
import { Check, FileText, Plus, Search, X } from 'lucide-react';

import { cn } from '../../lib/utils';
import type { TextbausteinApiDto, LeistungApiDto, ArbeitszeitartApiDto, DocBlock } from './types';
import { extractFontSizeFromHtml, extractBoldFromHtml, unitMap } from './helpers';

interface DocumentEditorSidebarProps {
    textbausteine: TextbausteinApiDto[];
    leistungen: LeistungApiDto[];
    arbeitszeitarten: ArbeitszeitartApiDto[];
    isLocked: boolean;
    isOpen: boolean;
    onClose: () => void;
    onAddBlock: (type: string, payload?: Partial<DocBlock>) => void;
    replacePlaceholders: (text: string) => string;
}

type TabType = 'TEXT' | 'SERVICE' | 'ARBEITSZEIT';

export function DocumentEditorSidebar({
    textbausteine,
    leistungen,
    arbeitszeitarten,
    isLocked,
    isOpen,
    onClose,
    onAddBlock,
    replacePlaceholders,
}: DocumentEditorSidebarProps) {
    const [activeTab, setActiveTab] = useState<TabType>('TEXT');
    const [searchQuery, setSearchQuery] = useState('');
    const [recentlyAdded, setRecentlyAdded] = useState<string | null>(null);
    const feedbackTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    useEffect(() => {
        return () => {
            if (feedbackTimerRef.current) clearTimeout(feedbackTimerRef.current);
        };
    }, []);

    const flashFeedback = (key: string) => {
        if (feedbackTimerRef.current) clearTimeout(feedbackTimerRef.current);
        setRecentlyAdded(key);
        feedbackTimerRef.current = setTimeout(() => setRecentlyAdded(null), 1200);
    };

    // Close on Escape
    useEffect(() => {
        if (!isOpen) return;
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'Escape') onClose();
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, onClose]);

    // Filtered items based on search
    const filteredTextbausteine = useMemo(() => {
        if (!searchQuery) return textbausteine;
        const q = searchQuery.toLowerCase();
        return textbausteine.filter(tb =>
            tb.name.toLowerCase().includes(q) || (tb.beschreibung || '').toLowerCase().includes(q)
        );
    }, [textbausteine, searchQuery]);

    const filteredLeistungen = useMemo(() => {
        if (!searchQuery) return leistungen;
        const q = searchQuery.toLowerCase();
        return leistungen.filter(l =>
            l.name.toLowerCase().includes(q) || (l.description || '').toLowerCase().includes(q)
        );
    }, [leistungen, searchQuery]);

    const filteredArbeitszeitarten = useMemo(() => {
        if (!searchQuery) return arbeitszeitarten;
        const q = searchQuery.toLowerCase();
        return arbeitszeitarten.filter(az =>
            az.bezeichnung.toLowerCase().includes(q) || (az.beschreibung || '').toLowerCase().includes(q)
        );
    }, [arbeitszeitarten, searchQuery]);

    const tabCounts: Record<TabType, number> = {
        TEXT: filteredTextbausteine.length,
        SERVICE: filteredLeistungen.length,
        ARBEITSZEIT: filteredArbeitszeitarten.length,
    };

    const handleAddTextblock = (tb: TextbausteinApiDto) => {
        const htmlContent = tb.html || tb.beschreibung || '';
        onAddBlock('TEXT', {
            content: replacePlaceholders(htmlContent),
            fontSize: extractFontSizeFromHtml(htmlContent),
            fett: extractBoldFromHtml(htmlContent),
        });
        flashFeedback(`TEXT-${tb.id}`);
    };

    const handleAddLeistung = (l: LeistungApiDto) => {
        const descHtml = l.description || '';
        onAddBlock('SERVICE', {
            title: l.name,
            description: descHtml,
            quantity: 1,
            unit: unitMap[l.unit?.name || 'STUECK'] || 'Stk',
            price: l.price,
            fontSize: extractFontSizeFromHtml(descHtml),
            fett: extractBoldFromHtml(descHtml),
            leistungId: l.id,
            kategorieId: l.folderId ?? undefined,
        });
        flashFeedback(`SERVICE-${l.id}`);
    };

    const handleAddArbeitszeitart = (az: ArbeitszeitartApiDto) => {
        const descHtml = az.beschreibung || '';
        onAddBlock('SERVICE', {
            title: az.bezeichnung,
            description: descHtml,
            quantity: 1,
            unit: 'h',
            price: az.stundensatz,
            fontSize: extractFontSizeFromHtml(descHtml),
            fett: extractBoldFromHtml(descHtml),
        });
        flashFeedback(`ARBEITSZEIT-${az.id}`);
    };

    if (!isOpen) return null;

    return (
        <>
            {/* Backdrop overlay */}
            <div
                className="absolute inset-0 z-30 bg-black/20 backdrop-blur-[1px] transition-opacity"
                onClick={onClose}
            />

            {/* Drawer panel */}
            <div className="absolute left-0 top-0 bottom-0 z-40 w-80 bg-white shadow-xl border-r border-slate-200 flex flex-col animate-in slide-in-from-left duration-200">
                {/* Header */}
                <div className="flex items-center justify-between px-3 py-2 border-b border-slate-200">
                    <span className="text-xs font-semibold text-slate-700 uppercase tracking-wide">Einfügen</span>
                    <button
                        onClick={onClose}
                        className="p-1 hover:bg-slate-100 rounded-md transition-colors"
                    >
                        <X className="w-3.5 h-3.5 text-slate-400" />
                    </button>
                </div>

                {/* Search */}
                <div className="px-3 pt-2 pb-2">
                    <div className="relative">
                        <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400" />
                        <input
                            type="text"
                            placeholder="Vorlagen durchsuchen…"
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            autoFocus
                            className="w-full pl-8 pr-3 py-2 text-xs bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-300 placeholder:text-slate-400 transition-all"
                        />
                    </div>
                </div>

                {/* Tabs */}
                <div className="px-3 pb-2">
                    <div className="flex bg-slate-100 p-0.5 rounded-lg">
                        {([
                            { key: 'TEXT' as TabType, label: 'Texte' },
                            { key: 'SERVICE' as TabType, label: 'Leistungen' },
                            { key: 'ARBEITSZEIT' as TabType, label: 'Stunden' },
                        ]).map(tab => (
                            <button
                                key={tab.key}
                                className={cn(
                                    "flex-1 py-1.5 text-[11px] font-medium rounded-md transition-all relative",
                                    activeTab === tab.key
                                        ? "bg-white shadow-sm text-slate-900"
                                        : "text-slate-500 hover:text-slate-700"
                                )}
                                onClick={() => setActiveTab(tab.key)}
                            >
                                {tab.label}
                                {tabCounts[tab.key] > 0 && (
                                    <span className={cn(
                                        "ml-1 text-[10px] font-normal",
                                        activeTab === tab.key ? "text-rose-500" : "text-slate-400"
                                    )}>
                                        {tabCounts[tab.key]}
                                    </span>
                                )}
                            </button>
                        ))}
                    </div>
                </div>

                {/* Item list */}
                <div className="flex-1 overflow-y-auto px-3 pb-3 space-y-1.5">
                    {activeTab === 'TEXT' && filteredTextbausteine.map(tb => {
                        const isAdded = recentlyAdded === `TEXT-${tb.id}`;
                        return (
                            <button
                                key={tb.id}
                                onClick={() => handleAddTextblock(tb)}
                                disabled={isLocked}
                                className={cn(
                                    "w-full group p-2.5 text-left border rounded-lg transition-all duration-150 disabled:opacity-50 disabled:pointer-events-none",
                                    isAdded
                                        ? "bg-rose-100 border-rose-400 ring-2 ring-rose-300 scale-[1.02] shadow-sm"
                                        : "bg-white hover:bg-rose-50 border-slate-150 hover:border-rose-200"
                                )}
                            >
                                <div className="flex items-start gap-2">
                                    <div className={cn(
                                        "mt-0.5 p-1 rounded transition-colors",
                                        isAdded ? "bg-rose-200" : "bg-slate-100 group-hover:bg-rose-100"
                                    )}>
                                        <FileText className={cn(
                                            "w-3 h-3 transition-colors",
                                            isAdded ? "text-rose-700" : "text-slate-400 group-hover:text-rose-500"
                                        )} />
                                    </div>
                                    <div className="min-w-0 flex-1">
                                        <span className={cn(
                                            "text-xs font-medium block truncate transition-colors",
                                            isAdded ? "text-rose-800" : "text-slate-700 group-hover:text-rose-700"
                                        )}>
                                            {tb.name}
                                        </span>
                                        {isAdded ? (
                                            <span className="text-[10px] font-medium text-rose-700 block truncate mt-0.5">
                                                Hinzugefügt
                                            </span>
                                        ) : tb.beschreibung && (
                                            <span className="text-[10px] text-slate-400 block truncate mt-0.5">
                                                {(() => { let t = tb.beschreibung; let p; do { p = t; t = t.replace(/<[^>]*>/g, ''); } while (t !== p); return t.slice(0, 60); })()}
                                            </span>
                                        )}
                                    </div>
                                    {isAdded ? (
                                        <Check className="w-3.5 h-3.5 text-rose-600 flex-shrink-0 mt-0.5" />
                                    ) : (
                                        <Plus className="w-3.5 h-3.5 text-slate-300 group-hover:text-rose-400 flex-shrink-0 mt-0.5" />
                                    )}
                                </div>
                            </button>
                        );
                    })}

                    {activeTab === 'SERVICE' && filteredLeistungen.map(l => {
                        const isAdded = recentlyAdded === `SERVICE-${l.id}`;
                        return (
                            <button
                                key={l.id}
                                onClick={() => handleAddLeistung(l)}
                                disabled={isLocked}
                                className={cn(
                                    "w-full group p-2.5 text-left border rounded-lg transition-all duration-150 disabled:opacity-50 disabled:pointer-events-none",
                                    isAdded
                                        ? "bg-rose-100 border-rose-400 ring-2 ring-rose-300 scale-[1.02] shadow-sm"
                                        : "bg-white hover:bg-rose-50 border-slate-150 hover:border-rose-200"
                                )}
                            >
                                <div className="flex items-center justify-between gap-2">
                                    <span className={cn(
                                        "text-xs font-medium truncate transition-colors flex items-center gap-1.5",
                                        isAdded ? "text-rose-800" : "text-slate-700 group-hover:text-rose-700"
                                    )}>
                                        {isAdded && <Check className="w-3 h-3 text-rose-600 flex-shrink-0" />}
                                        <span className="truncate">{isAdded ? 'Hinzugefügt: ' : ''}{l.name}</span>
                                    </span>
                                    <span className={cn(
                                        "text-[10px] font-semibold px-1.5 py-0.5 rounded flex-shrink-0 transition-colors",
                                        isAdded
                                            ? "bg-rose-200 text-rose-800"
                                            : "bg-slate-100 text-slate-500 group-hover:bg-rose-100 group-hover:text-rose-600"
                                    )}>
                                        {l.price?.toFixed(2)} €
                                    </span>
                                </div>
                            </button>
                        );
                    })}

                    {activeTab === 'ARBEITSZEIT' && filteredArbeitszeitarten.map(az => {
                        const isAdded = recentlyAdded === `ARBEITSZEIT-${az.id}`;
                        return (
                            <button
                                key={az.id}
                                onClick={() => handleAddArbeitszeitart(az)}
                                disabled={isLocked}
                                className={cn(
                                    "w-full group p-2.5 text-left border rounded-lg transition-all duration-150 disabled:opacity-50 disabled:pointer-events-none",
                                    isAdded
                                        ? "bg-rose-100 border-rose-400 ring-2 ring-rose-300 scale-[1.02] shadow-sm"
                                        : "bg-white hover:bg-rose-50 border-slate-150 hover:border-rose-200"
                                )}
                            >
                                <div className="flex items-center justify-between gap-2">
                                    <span className={cn(
                                        "text-xs font-medium truncate transition-colors flex items-center gap-1.5",
                                        isAdded ? "text-rose-800" : "text-slate-700 group-hover:text-rose-700"
                                    )}>
                                        {isAdded && <Check className="w-3 h-3 text-rose-600 flex-shrink-0" />}
                                        <span className="truncate">{isAdded ? 'Hinzugefügt: ' : ''}{az.bezeichnung}</span>
                                    </span>
                                    <span className={cn(
                                        "text-[10px] font-semibold px-1.5 py-0.5 rounded flex-shrink-0 transition-colors",
                                        isAdded
                                            ? "bg-rose-200 text-rose-800"
                                            : "bg-slate-100 text-slate-500 group-hover:bg-rose-100 group-hover:text-rose-600"
                                    )}>
                                        {az.stundensatz?.toFixed(2)} €/h
                                    </span>
                                </div>
                            </button>
                        );
                    })}

                    {/* Empty state */}
                    {((activeTab === 'TEXT' && filteredTextbausteine.length === 0) ||
                      (activeTab === 'SERVICE' && filteredLeistungen.length === 0) ||
                      (activeTab === 'ARBEITSZEIT' && filteredArbeitszeitarten.length === 0)) && (
                        <div className="py-8 text-center">
                            <Search className="w-8 h-8 text-slate-200 mx-auto mb-2" />
                            <p className="text-xs text-slate-400">
                                {searchQuery ? 'Keine Ergebnisse gefunden' : 'Keine Vorlagen vorhanden'}
                            </p>
                        </div>
                    )}
                </div>
            </div>
        </>
    );
}
