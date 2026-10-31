import { useState, useRef, useEffect, useCallback, useId, type ReactNode } from 'react';
import { cn } from '../../lib/utils';
import { Check, ChevronsUpDown, Search, X, Loader2, PencilLine } from 'lucide-react';

export interface SearchableSelectOption {
    value: string;
    label: string;
    sublabel?: string;
}

export interface SearchableSelectProps {
    value: string;
    onChange: (value: string) => void;
    options?: SearchableSelectOption[];
    /** Async search callback – when provided, options are fetched from backend instead of local filtering */
    onSearch?: (term: string) => Promise<SearchableSelectOption[]>;
    /** Label to show for the currently selected value when options haven't been loaded yet */
    selectedLabel?: string;
    /** Fester Text im Auswahlknopf für den gewählten Wert – hat Vorrang vor der Bezeichnung der Option. */
    anzeigeText?: string;
    placeholder?: string;
    searchPlaceholder?: string;
    className?: string;
    icon?: ReactNode;
    /** Name des Auswahlknopfs für Screenreader, wenn kein sichtbares Label daneben steht. */
    ariaLabel?: string;
    /** Id des Auswahlknopfs, z. B. für ein `<label htmlFor>`. */
    id?: string;
    disabled?: boolean;
    /** Wird beim Öffnen und Schließen gerufen – z. B. um die Liste erst beim Öffnen zu laden. */
    onOpenChange?: (offen: boolean) => void;
    /** Die (lokalen) Optionen werden gerade geladen. */
    loading?: boolean;
    /**
     * Zusätzliche Bedienelemente unter dem Suchfeld, z. B. Filter. Die Optionen
     * filtert die aufrufende Stelle selbst und gibt nur die passenden herein.
     */
    filter?: ReactNode;
    /** Hinweise oder Fehler über der Trefferliste (z. B. „Für Anbieter X fehlt der Schlüssel“). */
    meldungen?: ReactNode;
    /**
     * Erlaubt, den Suchbegriff selbst als Wert zu übernehmen, wenn der Eintrag
     * in der Liste fehlt (z. B. ein Modellname). Ohne Angabe: nur Listenwerte.
     */
    freieEingabe?: SearchableSelectFreieEingabe;
    /** Text, wenn die Suche nichts findet. */
    keineTrefferText?: string;
    /** Darf die Auswahl per X geleert werden? Standard: ja. */
    leerbar?: boolean;
    /** Name des X-Knopfs zum Leeren. */
    leerenText?: string;
}

export interface SearchableSelectFreieEingabe {
    /** Steht unter der Liste, solange nichts getippt ist. */
    hinweis: string;
    /** Text des Knopfs zum Übernehmen, z. B. „„xyz“ übernehmen“. */
    knopfText: (suchbegriff: string) => string;
    /** Prüft den Begriff vorher; gibt die Fehlermeldung zurück oder `null`. */
    pruefen?: (suchbegriff: string) => string | null;
}

/** Lokale Suche: Bezeichnung oder Zusatz enthält den Suchbegriff (ohne Groß-/Kleinschreibung). */
function filtereLokal(options: SearchableSelectOption[], searchTerm: string): SearchableSelectOption[] {
    if (!searchTerm) return options;
    const lower = searchTerm.toLowerCase();
    return options.filter(o =>
        o.label.toLowerCase().includes(lower) ||
        o.value.toLowerCase().includes(lower) ||
        (o.sublabel && o.sublabel.toLowerCase().includes(lower))
    );
}

/**
 * Auswahl mit Suche – für Listen, die für Knöpfe oder ein einfaches Klapp-Menü
 * zu lang sind.
 *
 * <p>Lokal (`options`, gefiltert beim Tippen) oder über den Server
 * (`onSearch`, verzögert). Optional mit Filtern (`filter`), Hinweisen
 * (`meldungen`) und freier Eingabe (`freieEingabe`), wenn der gesuchte
 * Eintrag in der Liste fehlt.</p>
 *
 * <p>Tastatur: Pfeil hoch/runter wandern durch die Treffer, Enter wählt,
 * Escape schließt und gibt den Fokus an den Auswahlknopf zurück.</p>
 */
export function SearchableSelect({
    value,
    onChange,
    options = [],
    onSearch,
    selectedLabel,
    anzeigeText,
    placeholder = 'Auswählen...',
    searchPlaceholder = 'Suchen...',
    className,
    icon,
    ariaLabel,
    id,
    disabled = false,
    onOpenChange,
    loading = false,
    filter,
    meldungen,
    freieEingabe,
    keineTrefferText,
    leerbar = true,
    leerenText = 'Auswahl leeren',
}: SearchableSelectProps) {
    const listId = useId();
    const [isOpen, setIsOpen] = useState(false);
    const [searchTerm, setSearchTerm] = useState('');
    /** Treffer der Server-Suche (nur mit onSearch). */
    const [asyncOptions, setAsyncOptions] = useState<SearchableSelectOption[]>(options);
    const [asyncLoading, setAsyncLoading] = useState(false);
    /** Per Pfeiltaste markierter Treffer. */
    const [aktiv, setAktiv] = useState(-1);
    /** Meldung, wenn ein frei eingetragener Begriff nicht passt. */
    const [freiFehler, setFreiFehler] = useState<string | null>(null);
    const wrapperRef = useRef<HTMLDivElement>(null);
    const triggerRef = useRef<HTMLButtonElement>(null);
    const inputRef = useRef<HTMLInputElement>(null);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    // Angezeigte Treffer: vom Server (onSearch) oder lokal beim Rendern gefiltert
    const filteredOptions = onSearch ? asyncOptions : filtereLokal(options, searchTerm);
    const laedt = asyncLoading || loading;

    // Get selected option label
    const selectedOption = options.find(o => o.value === value)
        || filteredOptions.find(o => o.value === value);
    const displayLabel = (value && anzeigeText) || selectedOption?.label || selectedLabel;

    const oeffnen = (offen: boolean) => {
        setIsOpen(offen);
        setAktiv(-1);
        setFreiFehler(null);
        if (!offen) setSearchTerm('');
        onOpenChange?.(offen);
    };

    // Async search handler (debounced)
    const handleAsyncSearch = useCallback((term: string) => {
        if (!onSearch) return;
        if (debounceRef.current) clearTimeout(debounceRef.current);
        debounceRef.current = setTimeout(async () => {
            setAsyncLoading(true);
            try {
                const results = await onSearch(term);
                setAsyncOptions(results);
            } catch {
                setAsyncOptions([]);
            } finally {
                setAsyncLoading(false);
            }
        }, 250);
    }, [onSearch]);

    // Load initial results when dropdown opens in async mode
    useEffect(() => {
        if (isOpen && onSearch && filteredOptions.length === 0 && !searchTerm) {
            handleAsyncSearch('');
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isOpen, onSearch]);

    // Handle click outside to close
    const onOpenChangeRef = useRef(onOpenChange);
    useEffect(() => { onOpenChangeRef.current = onOpenChange; }, [onOpenChange]);
    useEffect(() => {
        if (!isOpen) return;
        const handleClickOutside = (event: MouseEvent) => {
            if (wrapperRef.current && !wrapperRef.current.contains(event.target as Node)) {
                setIsOpen(false);
                setSearchTerm('');
                setAktiv(-1);
                onOpenChangeRef.current?.(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, [isOpen]);

    // Focus input when opening
    useEffect(() => {
        if (isOpen && inputRef.current) {
            setTimeout(() => inputRef.current?.focus(), 50);
        }
    }, [isOpen]);

    const schliessen = () => {
        oeffnen(false);
        triggerRef.current?.focus();
    };

    const handleSelect = (optionValue: string) => {
        onChange(optionValue);
        schliessen();
    };

    const freiUebernehmen = () => {
        const begriff = searchTerm.trim();
        const meldung = freieEingabe?.pruefen?.(begriff) ?? null;
        setFreiFehler(meldung);
        if (!meldung) handleSelect(begriff);
    };

    const handleClear = (e: React.MouseEvent | React.KeyboardEvent) => {
        e.stopPropagation();
        onChange('');
        setSearchTerm('');
    };

    const handleSuchTaste = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            setAktiv(i => Math.min(filteredOptions.length - 1, i + 1));
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            setAktiv(i => Math.max(0, i - 1));
        } else if (e.key === 'Enter') {
            const treffer = filteredOptions[aktiv];
            if (treffer) {
                e.preventDefault();
                handleSelect(treffer.value);
            } else if (freieEingabe && searchTerm.trim()) {
                // Nichts markiert: Enter übernimmt den getippten Begriff.
                e.preventDefault();
                freiUebernehmen();
            }
        }
    };

    /** Escape schließt die offene Liste – egal, ob der Fokus im Suchfeld, einem Filter oder auf dem Knopf steht. */
    const handleWrapperTaste = (e: React.KeyboardEvent<HTMLDivElement>) => {
        if (e.key !== 'Escape' || !isOpen) return;
        e.preventDefault();
        e.stopPropagation();
        schliessen();
    };

    const optionId = (index: number) => `${listId}-option-${index}`;

    return (
        <div className={cn("relative", className)} ref={wrapperRef} onKeyDown={handleWrapperTaste}>
            {/* Trigger Button */}
            <button
                ref={triggerRef}
                id={id}
                type="button"
                disabled={disabled}
                aria-label={ariaLabel}
                aria-haspopup="listbox"
                aria-expanded={isOpen}
                onClick={() => oeffnen(!isOpen)}
                className={cn(
                    "w-full flex items-center justify-between gap-2 px-3 py-2 text-left",
                    "bg-white border border-slate-300 rounded-md",
                    "hover:border-slate-400 transition-colors",
                    "focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500",
                    "disabled:cursor-not-allowed disabled:bg-slate-50 disabled:opacity-60",
                    isOpen && "ring-2 ring-rose-200 border-rose-300"
                )}
            >
                <div className="flex items-center gap-2 flex-1 min-w-0">
                    {icon && <span className="text-slate-400 shrink-0">{icon}</span>}
                    <span className={cn(
                        "truncate text-sm",
                        displayLabel ? "text-slate-900" : "text-slate-400"
                    )}>
                        {displayLabel || placeholder}
                    </span>
                </div>
                <div className="flex items-center gap-1 shrink-0">
                    {value && leerbar && !disabled && (
                        <span
                            role="button"
                            tabIndex={-1}
                            aria-label={leerenText}
                            title={leerenText}
                            onClick={handleClear}
                            className="p-0.5 hover:bg-slate-100 rounded transition-colors"
                        >
                            <X className="w-3 h-3 text-slate-400 hover:text-slate-600" />
                        </span>
                    )}
                    <ChevronsUpDown className="w-4 h-4 text-slate-400" />
                </div>
            </button>

            {/* Dropdown */}
            {isOpen && (
                <div className="absolute z-[60] w-full min-w-[18rem] mt-1 bg-white border border-slate-200 rounded-lg shadow-xl overflow-hidden animate-in fade-in zoom-in-95 duration-100">
                    {/* Search Input */}
                    <div className="p-2 border-b border-slate-100 space-y-2">
                        <div className="relative">
                            <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                            <input
                                ref={inputRef}
                                type="text"
                                role="combobox"
                                aria-label={searchPlaceholder}
                                aria-controls={listId}
                                aria-expanded="true"
                                aria-autocomplete="list"
                                aria-activedescendant={aktiv >= 0 ? optionId(aktiv) : undefined}
                                value={searchTerm}
                                onChange={(e) => {
                                    setSearchTerm(e.target.value);
                                    setAktiv(-1);
                                    setFreiFehler(null);
                                    if (onSearch) handleAsyncSearch(e.target.value);
                                }}
                                onKeyDown={handleSuchTaste}
                                placeholder={searchPlaceholder}
                                className="w-full pl-8 pr-3 py-2 text-sm border border-slate-200 rounded-md focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                            />
                        </div>
                        {filter}
                    </div>

                    {meldungen && <div className="border-b border-slate-100 px-3 py-2 text-xs text-slate-600">{meldungen}</div>}

                    {/* Options List */}
                    <ul id={listId} role="listbox" aria-label={ariaLabel ?? placeholder} className="max-h-60 overflow-auto py-1">
                        {laedt ? (
                            <li role="presentation" className="px-3 py-3 flex items-center justify-center gap-2 text-sm text-slate-500">
                                <Loader2 aria-hidden="true" className="w-4 h-4 motion-safe:animate-spin text-slate-400" />
                                Wird geladen …
                            </li>
                        ) : filteredOptions.length === 0 ? (
                            <li role="presentation" className="px-3 py-2 text-sm text-slate-400 text-center">
                                {keineTrefferText ?? (searchTerm ? 'Keine Ergebnisse gefunden' : 'Suchbegriff eingeben...')}
                            </li>
                        ) : (
                            filteredOptions.map((option, index) => (
                                <li
                                    key={option.value}
                                    id={optionId(index)}
                                    role="option"
                                    aria-selected={value === option.value}
                                    onClick={() => handleSelect(option.value)}
                                    onMouseEnter={() => setAktiv(index)}
                                    className={cn(
                                        "px-3 py-2 text-sm cursor-pointer flex items-center justify-between gap-2",
                                        "hover:bg-rose-50 hover:text-rose-700 transition-colors",
                                        (value === option.value || aktiv === index) && "bg-rose-50 text-rose-700"
                                    )}
                                >
                                    <div className="flex flex-col min-w-0">
                                        <span className="truncate font-medium">{option.label}</span>
                                        {option.sublabel && (
                                            <span className="truncate text-xs text-slate-400">{option.sublabel}</span>
                                        )}
                                    </div>
                                    {value === option.value && (
                                        <Check className="w-4 h-4 text-rose-600 shrink-0" />
                                    )}
                                </li>
                            ))
                        )}
                    </ul>
                    {freieEingabe && (
                        <div className="border-t border-slate-100 p-2">
                            {searchTerm.trim() ? (
                                <>
                                    <button
                                        type="button"
                                        onClick={freiUebernehmen}
                                        className="flex w-full items-center gap-2 rounded px-2 py-1.5 text-left text-sm text-rose-700 hover:bg-rose-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                                    >
                                        <PencilLine aria-hidden="true" className="h-4 w-4 shrink-0" />
                                        <span className="min-w-0 break-all">{freieEingabe.knopfText(searchTerm.trim())}</span>
                                    </button>
                                    {freiFehler && <p role="alert" className="px-2 pt-1 text-xs text-red-700">{freiFehler}</p>}
                                </>
                            ) : (
                                <p className="px-2 py-1 text-xs text-slate-500">{freieEingabe.hinweis}</p>
                            )}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
