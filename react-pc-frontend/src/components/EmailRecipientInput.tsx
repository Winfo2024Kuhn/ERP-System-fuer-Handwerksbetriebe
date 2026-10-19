
import { useState, useRef, useEffect, useLayoutEffect, useCallback, useMemo } from 'react';
import { createPortal } from 'react-dom';
import { Input } from './ui/input';
import { cn } from '../lib/utils';
import { X, Search, Building2, User, Briefcase, FileText, Mail, Loader2, Calculator, Plus, AlertTriangle } from 'lucide-react';
import { Button } from './ui/button';
import { formatRecipient } from '../lib/emailAddress';
import {
    enthaeltEmpfaenger, fuegeEmpfaengerHinzu, kaertchenText, teileEingabe, verbindeEmpfaenger, zerlegeEmpfaenger,
} from '../features/email/empfaengerListe';

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
    readOnly?: boolean;
    /**
     * Mehrere Empfänger als Kärtchen. `value` bleibt der kommagetrennte Text, enthält
     * aber nur fertige Kärtchen – was gerade getippt wird, kommt erst mit Enter, Komma,
     * Tab, Plus-Knopf oder beim Verlassen des Felds dazu.
     */
    mehrfach?: boolean;
    /** Name für Screenreader, z. B. „Empfänger“ oder „Kopie (CC)“. */
    ariaLabel?: string;
    /**
     * Mehrfach-Modus: meldet, ob gerade etwas getippt ist, das noch kein Kärtchen ist.
     * So kann das Formular „Senden“ freigeben – der Klick darauf verlässt das Feld und
     * macht das Getippte vorher zum Kärtchen.
     */
    onEntwurfChange?: (vorhanden: boolean) => void;
}

const TYPE_CONFIG: Record<string, { label: string; icon: typeof User; color: string; bg: string }> = {
    KUNDE: { label: 'Kunde', icon: User, color: 'text-emerald-700', bg: 'bg-emerald-50 border-emerald-200' },
    LIEFERANT: { label: 'Lieferant', icon: Building2, color: 'text-blue-700', bg: 'bg-blue-50 border-blue-200' },
    PROJEKT: { label: 'Projekt', icon: Briefcase, color: 'text-rose-700', bg: 'bg-rose-50 border-rose-200' },
    ANFRAGE: { label: 'Anfrage', icon: FileText, color: 'text-amber-700', bg: 'bg-amber-50 border-amber-200' },
    STEUERBERATER: { label: 'Steuerberater', icon: Calculator, color: 'text-violet-700', bg: 'bg-violet-50 border-violet-200' },
};

export function EmailRecipientInput({
    value,
    onChange,
    suggestions = [],
    placeholder = 'E-Mail eingeben...',
    onRemove,
    className,
    readOnly = false,
    mehrfach = false,
    ariaLabel,
    onEntwurfChange,
}: EmailRecipientInputProps) {
    const [isOpen, setIsOpen] = useState(false);
    /** Nur im Mehrfach-Modus: der Text, der noch kein Kärtchen ist. */
    const [entwurf, setEntwurf] = useState('');
    // Ohne Doppelte (alte Entwürfe können welche haben) – die Adresse ist damit ein eindeutiger Key.
    const kaertchen = useMemo(
        () => (mehrfach ? fuegeEmpfaengerHinzu([], zerlegeEmpfaenger(value)) : []),
        [mehrfach, value],
    );
    const suchtext = mehrfach ? entwurf : value;
    const entwurfVorhanden = entwurf.trim().length > 0;
    useEffect(() => {
        onEntwurfChange?.(entwurfVorhanden);
    }, [entwurfVorhanden, onEntwurfChange]);
    const [contacts, setContacts] = useState<ContactDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [highlightIdx, setHighlightIdx] = useState(-1);
    const wrapperRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLInputElement>(null);
    const dropdownRef = useRef<HTMLDivElement>(null);
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
        if (!suchtext || suchtext.length < 2) {
            setContacts([]);
            return;
        }
        debounceRef.current = setTimeout(() => {
            searchContacts(suchtext);
        }, 250);
        return () => {
            if (debounceRef.current) clearTimeout(debounceRef.current);
        };
    }, [suchtext, searchContacts]);

    // Click outside → close (must also check portal dropdown)
    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            const target = event.target as Node;
            const insideWrapper = wrapperRef.current?.contains(target);
            const insideDropdown = dropdownRef.current?.contains(target);
            if (!insideWrapper && !insideDropdown) {
                setIsOpen(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const showDropdown = isOpen && (linkedEmails.length > 0 || displayItems.length > 0 || loading || (suchtext && suchtext.length >= 2));

    // Dropdown-Position berechnen (Portal braucht fixed-Koordinaten als inline styles)
    // useLayoutEffect: läuft synchron nach DOM-Commit, vor Browser-Paint → kein Flicker
    useLayoutEffect(() => {
        if (!showDropdown || !wrapperRef.current) return;
        const el = wrapperRef.current;
        const updatePosition = () => {
            const dd = dropdownRef.current;
            if (!dd) return;
            const rect = el.getBoundingClientRect();
            dd.style.top = `${rect.bottom + 4}px`;
            dd.style.left = `${rect.left}px`;
            dd.style.width = `${rect.width}px`;
        };
        updatePosition();
        window.addEventListener('scroll', updatePosition, true);
        window.addEventListener('resize', updatePosition);
        return () => {
            window.removeEventListener('scroll', updatePosition, true);
            window.removeEventListener('resize', updatePosition);
        };
    }, [showDropdown]);

    // Reset highlight when items change
    useEffect(() => {
        setHighlightIdx(-1);
    }, [displayItems.length, linkedEmails.length]);

    /** Mehrfach-Modus: neue Empfänger als Kärtchen anhängen. */
    const uebernehme = (neue: string[]) => {
        const liste = fuegeEmpfaengerHinzu(kaertchen, neue);
        if (liste.length !== kaertchen.length) onChange(verbindeEmpfaenger(liste));
    };

    /** Mehrfach-Modus: das Getippte zum Kärtchen machen. */
    const uebernehmeEntwurf = () => {
        if (!entwurf.trim()) return false;
        uebernehme(zerlegeEmpfaenger(entwurf));
        setEntwurf('');
        return true;
    };

    const entferne = (index: number) => {
        onChange(verbindeEmpfaenger(kaertchen.filter((_, i) => i !== index)));
        inputRef.current?.focus();
    };

    /** `name` nur bei Kontakten aus der Suche – das Kärtchen zeigt dann den Namen statt der Adresse. */
    const handleSelect = (email: string, name?: string) => {
        if (mehrfach) {
            uebernehme([name ? formatRecipient(email, name) : email]);
            setEntwurf('');
            setIsOpen(false);
            setContacts([]);
            inputRef.current?.focus();
            return;
        }
        onChange(email);
        setIsOpen(false);
        setContacts([]);
        inputRef.current?.blur();
    };

    // Gesamtzahl aller auswählbaren Einträge (linked + contacts)
    const totalSelectableItems = linkedEmails.length + displayItems.length;

    const handleKeyDown = (e: React.KeyboardEvent) => {
        // Escape schließt auch die Liste „keine Treffer“ – sie verdeckt sonst die Felder darunter.
        if (e.key === 'Escape' && isOpen) {
            setIsOpen(false);
            return;
        }
        if (mehrfach && !readOnly) {
            const markiert = isOpen && highlightIdx >= 0 && highlightIdx < totalSelectableItems;
            if (e.key === 'Enter' && !markiert && entwurf.trim()) {
                e.preventDefault();
                uebernehmeEntwurf();
                return;
            }
            if (e.key === 'Tab' && entwurf.trim()) {
                uebernehmeEntwurf();
                return;
            }
            if (e.key === 'Backspace' && !entwurf && kaertchen.length > 0) {
                e.preventDefault();
                entferne(kaertchen.length - 1);
                return;
            }
        }
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
                if (item.type === 'contact') handleSelect(item.data.email, item.data.name);
                else handleSelect(item.email);
            }
        }
    };

    return (
        <div className={cn("relative", className)} ref={wrapperRef}>
            {mehrfach ? (
                <div
                    className={cn(
                        'flex min-h-10 w-full flex-wrap items-center gap-1.5 rounded-lg border border-slate-200 bg-white py-1.5 pl-3 pr-1.5 text-sm',
                        readOnly ? 'bg-slate-50 text-slate-500' : 'cursor-text focus-within:ring-2 focus-within:ring-rose-200 focus-within:border-rose-300',
                    )}
                    onMouseDown={(e) => {
                        // Klick auf freie Fläche setzt den Cursor ins Eingabefeld.
                        if (!readOnly && e.target === e.currentTarget) {
                            e.preventDefault();
                            inputRef.current?.focus();
                        }
                    }}
                >
                    <Search className="h-4 w-4 shrink-0 text-slate-400 pointer-events-none" aria-hidden="true" />
                    <ul role="list" className="contents" aria-label={ariaLabel ? `${ariaLabel}: ${kaertchen.length} eingetragen` : undefined}>
                        {kaertchen.map((eintrag, index) => {
                            const { anzeige, adresse, gueltig } = kaertchenText(eintrag);
                            return (
                                <li
                                    key={adresse.toLowerCase()}
                                    data-testid="empfaenger-kaertchen"
                                    title={gueltig ? eintrag : `${eintrag} – das sieht nicht wie eine E-Mail-Adresse aus`}
                                    className={cn(
                                        'inline-flex max-w-full items-center gap-1 rounded-md border py-0.5 pl-2 text-sm',
                                        readOnly ? 'pr-2' : 'pr-0.5',
                                        gueltig ? 'border-slate-200 bg-slate-100 text-slate-800' : 'border-amber-300 bg-amber-50 text-amber-900',
                                    )}
                                >
                                    {!gueltig && <AlertTriangle className="h-3.5 w-3.5 shrink-0 text-amber-600" aria-hidden="true" />}
                                    <span className="truncate">{anzeige}</span>
                                    {anzeige !== adresse && <span className="hidden truncate text-xs text-slate-500 sm:inline">{adresse}</span>}
                                    {!readOnly && (
                                        <button
                                            type="button"
                                            onClick={() => entferne(index)}
                                            aria-label={`${anzeige} entfernen`}
                                            className="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-slate-400 hover:bg-slate-200 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500"
                                        >
                                            <X className="h-3.5 w-3.5" aria-hidden="true" />
                                        </button>
                                    )}
                                </li>
                            );
                        })}
                    </ul>
                    {!readOnly && (
                        <input
                            ref={inputRef}
                            value={entwurf}
                            aria-label={ariaLabel}
                            onChange={(e) => {
                                const { fertig, rest } = teileEingabe(e.target.value);
                                if (fertig.length > 0) uebernehme(fertig);
                                setEntwurf(rest);
                                setIsOpen(true);
                            }}
                            onFocus={() => setIsOpen(true)}
                            onBlur={() => { uebernehmeEntwurf(); }}
                            onKeyDown={handleKeyDown}
                            onPaste={(e) => {
                                // Eingefügte Listen (z. B. aus Excel, eine Adresse pro Zeile) sofort zerlegen.
                                const text = e.clipboardData.getData('text');
                                if (!/[\n,;]/.test(text)) return;
                                e.preventDefault();
                                uebernehme(zerlegeEmpfaenger((entwurf + text).replace(/[\r\n\t]+/g, ',')));
                                setEntwurf('');
                            }}
                            placeholder={kaertchen.length > 0 ? 'Weitere hinzufügen …' : placeholder}
                            className="h-7 min-w-[10rem] flex-1 border-0 bg-transparent p-0 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-0"
                        />
                    )}
                    {loading && <Loader2 className="h-4 w-4 shrink-0 animate-spin text-rose-400" aria-hidden="true" />}
                    {!readOnly && (
                        <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => {
                                if (!uebernehmeEntwurf()) setIsOpen(true);
                                inputRef.current?.focus();
                            }}
                            aria-label={entwurf.trim() ? `„${entwurf.trim()}“ als Empfänger hinzufügen` : 'Weiteren Empfänger hinzufügen'}
                            title="Weiteren Empfänger hinzufügen"
                            className="ml-auto h-7 w-7 shrink-0 p-0"
                        >
                            <Plus className="h-4 w-4" aria-hidden="true" />
                        </Button>
                    )}
                </div>
            ) : (
            <div className="relative flex items-center gap-2">
                <div className="relative flex-1">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
                    <Input
                        ref={inputRef}
                        value={value}
                        onChange={(e) => {
                            if (readOnly) return;
                            onChange(e.target.value);
                            setIsOpen(true);
                        }}
                        onFocus={() => { if (!readOnly) setIsOpen(true); }}
                        onKeyDown={handleKeyDown}
                        placeholder={placeholder}
                        readOnly={readOnly}
                        className={cn(
                            "w-full pl-9 pr-8",
                            readOnly ? "bg-slate-50 text-slate-500 cursor-default" : "",
                            !readOnly && isOpen && "ring-2 ring-rose-200 border-rose-300"
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
            )}

            {/* Premium Dropdown – via Portal gerendert, damit overflow:hidden der Eltern nicht abschneidet */}
            {showDropdown && createPortal(
                <div ref={dropdownRef} className="email-recipient-dropdown bg-white border border-slate-200 rounded-xl shadow-xl max-h-80 overflow-auto animate-in fade-in zoom-in-95 duration-150">
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
                                    const isCurrentValue = mehrfach
                                        ? enthaeltEmpfaenger(kaertchen, email)
                                        : value.toLowerCase() === email.toLowerCase();
                                    return (
                                        <li
                                            key={'linked_' + email}
                                            onMouseDown={(e) => e.preventDefault()}
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
                                                onMouseDown={(e) => e.preventDefault()}
                                                onClick={() => handleSelect(c.email, c.name)}
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
                                                onMouseDown={(e) => e.preventDefault()}
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
                    {linkedEmails.length === 0 && displayItems.length === 0 && !loading && suchtext && suchtext.length >= 2 && (
                        <div className="px-4 py-6 text-center text-slate-400">
                            <Mail className="w-6 h-6 mx-auto mb-1.5 opacity-40" />
                            <p className="text-sm">Keine Kontakte gefunden</p>
                            <p className="text-xs mt-0.5">Drücke Enter um „{suchtext}" direkt zu verwenden</p>
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
                    {suchtext && suchtext.length >= 2 && (
                        <div className="px-3 py-2 border-t border-slate-100 bg-slate-50/50">
                            <p className="text-[11px] text-slate-400">
                                {displayItems.length} Treffer · Suche in Kunden, Lieferanten, Projekten, Anfragen & Steuerberatern
                            </p>
                        </div>
                    )}
                </div>,
                document.body
            )}
        </div>
    );
}
