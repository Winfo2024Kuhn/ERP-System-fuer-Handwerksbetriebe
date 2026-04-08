
import { useState, useRef, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { Input } from './ui/input';
import { cn } from '../lib/utils';
import { X, Search, Building2, User, Briefcase, FileText, Mail, Loader2 } from 'lucide-react';
import { Button } from './ui/button';

interface ContactDto {
    id: string;
    name: string;
    email: string;
    type: string; // KUNDE, LIEFERANT, PROJEKT, ANFRAGE
    context?: string;
}

export interface EmailRecipientInputProps {
    value: string;
    onChange: (value: string) => void;
    suggestions?: string[];
    placeholder?: string;
    label?: string;
    onRemove?: () => void;
    className?: string;
}

const TYPE_CONFIG: Record<string, { label: string; icon: typeof User; color: string; bg: string }> = {
    KUNDE: { label: 'Kunde', icon: User, color: 'text-emerald-700', bg: 'bg-emerald-50 border-emerald-200' },
    LIEFERANT: { label: 'Lieferant', icon: Building2, color: 'text-blue-700', bg: 'bg-blue-50 border-blue-200' },
    PROJEKT: { label: 'Projekt', icon: Briefcase, color: 'text-rose-700', bg: 'bg-rose-50 border-rose-200' },
    ANFRAGE: { label: 'Anfrage', icon: FileText, color: 'text-amber-700', bg: 'bg-amber-50 border-amber-200' },
};

export function EmailRecipientInput({
    value,
    onChange,
    suggestions = [],
    placeholder = 'E-Mail eingeben...',
    onRemove,
    className
}: EmailRecipientInputProps) {
    const [isOpen, setIsOpen] = useState(false);
    const [contacts, setContacts] = useState<ContactDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [highlightIdx, setHighlightIdx] = useState(-1);
    const wrapperRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLInputElement>(null);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const abortRef = useRef<AbortController | null>(null);

    // Server-seitige Kontaktsuche
    const searchContacts = useCallback(async (term: string) => {
        if (!term || term.length < 2) {
            setContacts([]);
            return;
        }

        if (abortRef.current) abortRef.current.abort();
        const controller = new AbortController();
        abortRef.current = controller;
        setLoading(true);

        try {
            const res = await fetch(`/api/emails/contacts?q=${encodeURIComponent(term)}`, {
                signal: controller.signal
            });
            if (res.ok) {
                const data: ContactDto[] = await res.json();
                setContacts(data);
            }
        } catch (e: unknown) {
            if (e instanceof Error && e.name !== 'AbortError') {
                console.error('Kontaktsuche fehlgeschlagen:', e);
            }
        } finally {
            if (!controller.signal.aborted) setLoading(false);
        }
    }, []);

    // Alle anzeigbaren Einträge: Verknüpfte Emails immer anzeigen, Server-Kontakte separat
    const linkedEmails = (() => {
        const items: string[] = [];
        const seen = new Set<string>();
        suggestions.forEach(email => {
            const emailLower = email.toLowerCase();
            if (!seen.has(emailLower)) {
                seen.add(emailLower);
                items.push(email);
            }
        });
        return items;
    })();

    const displayItems = (() => {
        const items: Array<{ type: 'contact'; data: ContactDto } | { type: 'email'; email: string }> = [];
        const seen = new Set<string>();

        // Verknüpfte Emails aus seen ausschließen (werden separat gerendert)
        linkedEmails.forEach(e => seen.add(e.toLowerCase()));

        // Server-Kontakte (nur solche, die nicht schon als linked email vorhanden)
        contacts.forEach(c => {
            if (!seen.has(c.email.toLowerCase())) {
                seen.add(c.email.toLowerCase());
                items.push({ type: 'contact', data: c });
            }
        });

        return items;
    })();

    // Debounced Suche
    useEffect(() => {
        if (debounceRef.current) clearTimeout(debounceRef.current);
        if (!value || value.length < 2) {
            setContacts([]);
            return;
        }
        debounceRef.current = setTimeout(() => {
            searchContacts(value);
        }, 250);
        return () => {
            if (debounceRef.current) clearTimeout(debounceRef.current);
        };
    }, [value, searchContacts]);

    // Click outside → close
    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (wrapperRef.current && !wrapperRef.current.contains(event.target as Node)) {
                setIsOpen(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    // Dropdown-Position berechnen (Portal braucht fixed-Koordinaten via CSS-Custom-Properties)
    useEffect(() => {
        if (!isOpen || !wrapperRef.current) return;
        const el = wrapperRef.current;
        const updatePosition = () => {
            const rect = el.getBoundingClientRect();
            el.style.setProperty('--dropdown-top', `${rect.bottom + 4}px`);
            el.style.setProperty('--dropdown-left', `${rect.left}px`);
            el.style.setProperty('--dropdown-width', `${rect.width}px`);
        };
        updatePosition();
        window.addEventListener('scroll', updatePosition, true);
        window.addEventListener('resize', updatePosition);
        return () => {
            window.removeEventListener('scroll', updatePosition, true);
            window.removeEventListener('resize', updatePosition);
        };
    }, [isOpen]);

    // Reset highlight when items change
    useEffect(() => {
        setHighlightIdx(-1);
    }, [displayItems.length, linkedEmails.length]);

    const handleSelect = (email: string) => {
        onChange(email);
        setIsOpen(false);
        setContacts([]);
        inputRef.current?.blur();
    };

    // Gesamtzahl aller auswählbaren Einträge (linked + contacts)
    const totalSelectableItems = linkedEmails.length + displayItems.length;

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (!isOpen || totalSelectableItems === 0) return;

        if (e.key === 'ArrowDown') {
            e.preventDefault();
            setHighlightIdx(prev => Math.min(prev + 1, totalSelectableItems - 1));
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            setHighlightIdx(prev => Math.max(prev - 1, 0));
        } else if (e.key === 'Enter' && highlightIdx >= 0) {
            e.preventDefault();
            if (highlightIdx < linkedEmails.length) {
                handleSelect(linkedEmails[highlightIdx]);
            } else {
                const item = displayItems[highlightIdx - linkedEmails.length];
                handleSelect(item.type === 'contact' ? item.data.email : item.email);
            }
        } else if (e.key === 'Escape') {
            setIsOpen(false);
        }
    };

    const showDropdown = isOpen && (linkedEmails.length > 0 || displayItems.length > 0 || loading || (value && value.length >= 2));

    return (
        <div className={cn("relative", className)} ref={wrapperRef}>
            <div className="relative flex items-center gap-2">
                <div className="relative flex-1">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
                    <Input
                        ref={inputRef}
                        value={value}
                        onChange={(e) => {
                            onChange(e.target.value);
                            setIsOpen(true);
                        }}
                        onFocus={() => setIsOpen(true)}
                        onKeyDown={handleKeyDown}
                        placeholder={placeholder}
                        className={cn(
                            "w-full pl-9 pr-8",
                            isOpen && "ring-2 ring-rose-200 border-rose-300"
                        )}
                    />
                    {loading && (
                        <Loader2 className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-rose-400 animate-spin" />
                    )}
                </div>

                {onRemove && (
                    <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={onRemove}
                        className="text-slate-400 hover:text-red-500"
                    >
                        <X className="w-4 h-4" />
                    </Button>
                )}
            </div>

            {/* Premium Dropdown – via Portal gerendert, damit overflow:hidden der Eltern nicht abschneidet */}
            {showDropdown && createPortal(
                <div className="email-recipient-dropdown bg-white border border-slate-200 rounded-xl shadow-xl max-h-80 overflow-auto animate-in fade-in zoom-in-95 duration-150">
                    {/* Verknüpfte E-Mail-Adressen (immer sichtbar wenn vorhanden) */}
                    {linkedEmails.length > 0 && (
                        <>
                            <div className="px-3 py-2 bg-slate-50 border-b border-slate-100">
                                <p className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider flex items-center gap-1.5">
                                    <Mail className="w-3 h-3" />
                                    Verknüpfte E-Mail-Adressen
                                </p>
                            </div>
                            <ul className="py-1">
                                {linkedEmails.map((email, idx) => {
                                    const isHighlighted = idx === highlightIdx;
                                    const isCurrentValue = value.toLowerCase() === email.toLowerCase();
                                    return (
                                        <li
                                            key={'linked_' + email}
                                            onClick={() => handleSelect(email)}
                                            onMouseEnter={() => setHighlightIdx(idx)}
                                            className={cn(
                                                "px-3 py-2.5 cursor-pointer transition-colors flex items-center gap-3",
                                                isHighlighted ? "bg-rose-50" : "hover:bg-slate-50"
                                            )}
                                        >
                                            <div className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 bg-rose-50 border border-rose-200">
                                                <Mail className="w-4 h-4 text-rose-600" />
                                            </div>
                                            <div className="flex-1 min-w-0">
                                                <span className="text-sm font-medium text-slate-800 truncate block">{email}</span>
                                            </div>
                                            {isCurrentValue && (
                                                <span className="text-[10px] font-semibold text-rose-600 bg-rose-50 border border-rose-200 px-1.5 py-0.5 rounded flex-shrink-0">
                                                    Ausgewählt
                                                </span>
                                            )}
                                        </li>
                                    );
                                })}
                            </ul>
                        </>
                    )}

                    {/* Server-Kontaktsuche (wenn Suchergebnisse vorhanden) */}
                    {displayItems.length > 0 && (
                        <>
                            <div className={cn("px-3 py-2 bg-slate-50 border-b border-slate-100", linkedEmails.length > 0 && "border-t")}>
                                <p className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider flex items-center gap-1.5">
                                    <Search className="w-3 h-3" />
                                    Suchergebnisse
                                </p>
                            </div>
                            <ul className="py-1">
                                {displayItems.map((item, idx) => {
                                    const globalIdx = linkedEmails.length + idx;
                                    if (item.type === 'contact') {
                                        const c = item.data;
                                        const cfg = TYPE_CONFIG[c.type] || TYPE_CONFIG.KUNDE;
                                        const Icon = cfg.icon;
                                        const isHighlighted = globalIdx === highlightIdx;

                                        return (
                                            <li
                                                key={c.id + '_' + c.email}
                                                onClick={() => handleSelect(c.email)}
                                                onMouseEnter={() => setHighlightIdx(globalIdx)}
                                                className={cn(
                                                    "px-3 py-2.5 cursor-pointer transition-colors",
                                                    isHighlighted ? "bg-rose-50" : "hover:bg-slate-50"
                                                )}
                                            >
                                                <div className="flex items-start gap-3">
                                                    <div className={cn("w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5", cfg.bg)}>
                                                        <Icon className={cn("w-4 h-4", cfg.color)} />
                                                    </div>
                                                    <div className="flex-1 min-w-0">
                                                        <div className="flex items-center gap-2">
                                                            <span className="font-medium text-sm text-slate-900 truncate">
                                                                {c.name}
                                                            </span>
                                                            <span className={cn("text-[10px] font-semibold uppercase px-1.5 py-0.5 rounded border flex-shrink-0", cfg.bg, cfg.color)}>
                                                                {cfg.label}
                                                            </span>
                                                        </div>
                                                        <p className="text-sm text-slate-600 truncate">{c.email}</p>
                                                        {c.context && (
                                                            <p className="text-xs text-slate-400 truncate mt-0.5">{c.context}</p>
                                                        )}
                                                    </div>
                                                </div>
                                            </li>
                                        );
                                    } else {
                                        const isHighlighted = globalIdx === highlightIdx;
                                        return (
                                            <li
                                                key={'email_' + item.email}
                                                onClick={() => handleSelect(item.email)}
                                                onMouseEnter={() => setHighlightIdx(globalIdx)}
                                                className={cn(
                                                    "px-3 py-2 cursor-pointer transition-colors flex items-center gap-3",
                                                    isHighlighted ? "bg-rose-50" : "hover:bg-slate-50"
                                                )}
                                            >
                                                <div className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 bg-slate-100">
                                                    <Mail className="w-4 h-4 text-slate-500" />
                                                </div>
                                                <span className="text-sm text-slate-700">{item.email}</span>
                                            </li>
                                        );
                                    }
                                })}
                            </ul>
                        </>
                    )}

                    {/* Leer-Zustand bei Suche ohne Ergebnis */}
                    {linkedEmails.length === 0 && displayItems.length === 0 && !loading && value && value.length >= 2 && (
                        <div className="px-4 py-6 text-center text-slate-400">
                            <Mail className="w-6 h-6 mx-auto mb-1.5 opacity-40" />
                            <p className="text-sm">Keine Kontakte gefunden</p>
                            <p className="text-xs mt-0.5">Drücke Enter um „{value}" direkt zu verwenden</p>
                        </div>
                    )}

                    {/* Lade-Indikator */}
                    {loading && displayItems.length === 0 && (
                        <div className="px-4 py-3 flex items-center justify-center gap-2 text-slate-400">
                            <Loader2 className="w-4 h-4 animate-spin" />
                            <span className="text-xs">Suche läuft...</span>
                        </div>
                    )}

                    {/* Footer */}
                    {value && value.length >= 2 && (
                        <div className="px-3 py-2 border-t border-slate-100 bg-slate-50/50">
                            <p className="text-[11px] text-slate-400">
                                {displayItems.length} Treffer · Suche in Kunden, Lieferanten, Projekten & Anfragenn
                            </p>
                        </div>
                    )}
                </div>,
                document.body
            )}
        </div>
    );
}
