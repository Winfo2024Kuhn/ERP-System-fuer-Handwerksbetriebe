import { useState, useRef, useEffect, useLayoutEffect, useId } from 'react';
import ReactDOM from 'react-dom';
import { Calendar, ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '../../lib/utils';
import { isoDatum, parseIsoDatum, parseDeutschesDatum } from '../../lib/datum';

export interface DatePickerProps {
    value: string;
    onChange: (value: string) => void;
    placeholder?: string;
    className?: string;
    disabled?: boolean;
    id?: string;
    name?: string;
    required?: boolean;
    min?: string;
    max?: string;
    error?: string;
    'aria-label'?: string;
    'aria-describedby'?: string;
}
const MONTHS = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];
const MONTHS_KURZ = ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez'];
const WEEKDAYS = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
/** Jahre pro Seite in der Jahresauswahl (3 Spalten x 4 Zeilen). */
const JAHRE_PRO_SEITE = 12;
const isoDate = isoDatum;
const parseDate = parseIsoDatum;
const displayDate = (date: Date) => date.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });

type Ansicht = 'tage' | 'monate' | 'jahre';

export function DatePicker({ value, onChange, placeholder = 'Datum wählen', className, disabled, id, name, required, min, max, error, 'aria-label': ariaLabel, 'aria-describedby': describedBy }: DatePickerProps) {
    const generatedId = useId(); const inputId = id ?? generatedId;
    const [isOpen, setIsOpen] = useState(false);
    const [view, setView] = useState(() => new Date());
    const [ansicht, setAnsicht] = useState<Ansicht>('tage');
    const [entwurf, setEntwurf] = useState('');
    const [entwurfFehler, setEntwurfFehler] = useState('');
    const [activeDate, setActiveDate] = useState('');
    const [attempted, setAttempted] = useState(false);
    const [position, setPosition] = useState({ top: 0, left: 0, width: 288, maxHeight: 400 });
    const triggerRef = useRef<HTMLButtonElement>(null);
    const popupRef = useRef<HTMLDivElement>(null);
    const validationRef = useRef<HTMLInputElement>(null);
    const selectedDate = parseDate(value);
    const minValue = min && parseDate(min) ? min : undefined;
    const maxValue = max && parseDate(max) ? max : undefined;
    const allowed = (date: string) => (!minValue || date >= minValue) && (!maxValue || date <= maxValue);
    const label = ariaLabel ?? 'Datum';
    const validationMessage = !value ? (required ? `Bitte ${label} auswählen.` : '') : !selectedDate ? `Bitte ein gültiges ${label} auswählen.` : !allowed(value) ? `${label} liegt außerhalb des erlaubten Zeitraums.` : '';
    const shownError = error || (attempted ? validationMessage : '');
    useEffect(() => { validationRef.current?.setCustomValidity(validationMessage); }, [validationMessage]);
    const close = () => { setIsOpen(false); triggerRef.current?.focus(); };
    const choose = (date: string) => { if (date && !allowed(date)) return; onChange(date); close(); };
    const open = () => {
        let date = selectedDate ? value : isoDate(new Date());
        if (minValue && date < minValue) date = minValue;
        if (maxValue && date > maxValue) date = maxValue;
        setView(parseDate(date) ?? new Date()); setActiveDate(date);
        setAnsicht('tage'); setEntwurf(''); setEntwurfFehler(''); setIsOpen(true);
    };
    useLayoutEffect(() => {
        if (!isOpen || disabled) return;
        const update = () => {
            const rect = triggerRef.current?.getBoundingClientRect(); if (!rect) return;
            const height = Math.min(440, Math.max(120, window.innerHeight - 16));
            const below = window.innerHeight - rect.bottom - 12;
            const upward = below < height && rect.top > below;
            const maxHeight = Math.max(80, Math.min(height, upward ? rect.top - 12 : below));
            const width = Math.min(304, window.innerWidth - 16);
            setPosition({ top: upward ? Math.max(8, rect.top - maxHeight - 4) : rect.bottom + 4, left: Math.max(8, Math.min(rect.left, window.innerWidth - width - 8)), width, maxHeight });
        };
        const outside = (event: MouseEvent) => { const target = event.target as Node; if (!triggerRef.current?.contains(target) && !popupRef.current?.contains(target)) setIsOpen(false); };
        update(); window.addEventListener('resize', update); window.addEventListener('scroll', update, true); document.addEventListener('mousedown', outside);
        return () => { window.removeEventListener('resize', update); window.removeEventListener('scroll', update, true); document.removeEventListener('mousedown', outside); };
    }, [isOpen, disabled]);
    useEffect(() => {
        if (isOpen && !disabled && ansicht === 'tage') popupRef.current?.querySelector<HTMLButtonElement>(`[data-date="${activeDate}"]:not(:disabled)`)?.focus();
    }, [isOpen, activeDate, disabled, ansicht]);
    const days = new Date(view.getFullYear(), view.getMonth() + 1, 0).getDate();
    const first = (new Date(view.getFullYear(), view.getMonth(), 1).getDay() + 6) % 7;
    const today = isoDate(new Date());
    const firstVisible = isoDate(new Date(view.getFullYear(), view.getMonth(), 1));
    const lastVisible = isoDate(new Date(view.getFullYear(), view.getMonth(), days));
    const focusDate = activeDate >= firstVisible && activeDate <= lastVisible ? activeDate : (minValue && minValue > firstVisible && minValue <= lastVisible ? minValue : firstVisible);
    const navigate = (offset: number) => setView(new Date(view.getFullYear(), view.getMonth() + offset, 1));

    // Grenzen je Ansicht: ein ganzer Monat/ein ganzes Jahr ist erreichbar,
    // sobald irgendein Tag darin erlaubt ist.
    const monatErlaubt = (jahr: number, monat: number) =>
        (!maxValue || isoDate(new Date(jahr, monat, 1)) <= maxValue)
        && (!minValue || isoDate(new Date(jahr, monat + 1, 0)) >= minValue);
    const jahrErlaubt = (jahr: number) =>
        (!maxValue || `${jahr}-01-01` <= maxValue) && (!minValue || `${jahr}-12-31` >= minValue);
    const jahresSeite = Math.floor(view.getFullYear() / JAHRE_PRO_SEITE) * JAHRE_PRO_SEITE;
    const jahre = Array.from({ length: JAHRE_PRO_SEITE }, (_, index) => jahresSeite + index);

    const canPrevious = ansicht === 'tage' ? (!minValue || minValue < firstVisible)
        : ansicht === 'monate' ? jahrErlaubt(view.getFullYear() - 1)
            : jahrErlaubt(jahresSeite - 1);
    const canNext = ansicht === 'tage' ? (!maxValue || maxValue > lastVisible)
        : ansicht === 'monate' ? jahrErlaubt(view.getFullYear() + 1)
            : jahrErlaubt(jahresSeite + JAHRE_PRO_SEITE);
    const zurueck = () => ansicht === 'tage' ? navigate(-1)
        : ansicht === 'monate' ? setView(new Date(view.getFullYear() - 1, view.getMonth(), 1))
            : setView(new Date(jahresSeite - JAHRE_PRO_SEITE, view.getMonth(), 1));
    const vor = () => ansicht === 'tage' ? navigate(1)
        : ansicht === 'monate' ? setView(new Date(view.getFullYear() + 1, view.getMonth(), 1))
            : setView(new Date(jahresSeite + JAHRE_PRO_SEITE, view.getMonth(), 1));
    const zurueckLabel = ansicht === 'tage' ? 'Vorheriger Monat' : ansicht === 'monate' ? 'Vorheriges Jahr' : 'Frühere Jahre';
    const vorLabel = ansicht === 'tage' ? 'Nächster Monat' : ansicht === 'monate' ? 'Nächstes Jahr' : 'Spätere Jahre';
    const kopfText = ansicht === 'tage' ? `${MONTHS[view.getMonth()]} ${view.getFullYear()}`
        : ansicht === 'monate' ? String(view.getFullYear())
            : `${jahresSeite} – ${jahresSeite + JAHRE_PRO_SEITE - 1}`;
    const kopfLabel = ansicht === 'tage' ? 'Monat und Jahr wählen' : ansicht === 'monate' ? 'Jahr wählen' : 'Zurück zur Monatsauswahl';

    /** Übernimmt die Direkteingabe; leer schließt nichts und meldet nichts. */
    const uebernehmeEntwurf = () => {
        if (!entwurf.trim()) { setEntwurfFehler(''); return; }
        const iso = parseDeutschesDatum(entwurf);
        if (!iso) { setEntwurfFehler('Bitte als TT.MM.JJJJ eingeben, zum Beispiel 09.09.1967.'); return; }
        if (!allowed(iso)) { setEntwurfFehler(`${label} liegt außerhalb des erlaubten Zeitraums.`); return; }
        setEntwurfFehler(''); choose(iso);
    };
    const gitterKlasse = 'h-10 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:cursor-not-allowed disabled:text-slate-300';

    return <div className={cn('relative w-full', className)}>
        <button ref={triggerRef} id={inputId} type="button" aria-label={ariaLabel} disabled={disabled} aria-haspopup="dialog" aria-expanded={isOpen && !disabled}
            aria-controls={isOpen ? `${inputId}-calendar` : undefined} aria-required={required} aria-invalid={shownError ? true : undefined}
            aria-describedby={[describedBy, shownError ? `${inputId}-error` : undefined].filter(Boolean).join(' ') || undefined}
            className="flex h-10 w-full items-center justify-between rounded-md border border-slate-200 bg-white px-3 py-2 text-left text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:cursor-not-allowed disabled:opacity-50 hover:bg-slate-50"
            onClick={() => isOpen ? close() : open()}>
            <span className={cn('truncate', !selectedDate && 'text-slate-500')}>{selectedDate ? displayDate(selectedDate) : placeholder}</span>
            <Calendar aria-hidden="true" className="h-4 w-4 shrink-0 opacity-50" />
        </button>
        <input ref={validationRef} name={name} type="text" tabIndex={-1} aria-hidden="true" className="sr-only" value={value} required={required} disabled={disabled} onChange={() => {}}
            onInvalid={event => { event.preventDefault(); setAttempted(true); triggerRef.current?.focus(); }} />
        {shownError && <p id={`${inputId}-error`} role="alert" className="mt-1 text-sm text-rose-700">{shownError}</p>}
        {isOpen && !disabled && ReactDOM.createPortal(<div ref={popupRef} id={`${inputId}-calendar`} role="dialog" aria-label={`${label} auswählen`}
            className="overflow-auto rounded-lg border border-slate-200 bg-white p-3 shadow-2xl" style={{ position: 'fixed', ...position, zIndex: 99999 }}
            onKeyDown={event => {
                if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close(); }
                if (event.key === 'Tab') {
                    const elemente = Array.from(popupRef.current?.querySelectorAll<HTMLElement>('button:not(:disabled):not([tabindex="-1"]), input:not(:disabled)') ?? []);
                    if ((event.shiftKey && event.target === elemente[0]) || (!event.shiftKey && event.target === elemente.at(-1))) {
                        if (event.shiftKey) event.preventDefault(); close();
                    }
                }
            }}>
            {/* Schnellster Weg zu einem weit entfernten Datum (Geburtsdatum):
                tippen statt klicken. Ein vollstaendiges Datum springt sofort in
                die Ansicht, uebernommen wird erst mit Enter oder "Übernehmen". */}
            <div className="mb-3">
                <label htmlFor={`${inputId}-eingabe`} className="mb-1 block text-xs font-medium text-slate-600">Datum eingeben</label>
                <div className="flex gap-2">
                    <input id={`${inputId}-eingabe`} type="text" inputMode="numeric" autoComplete="off" placeholder="TT.MM.JJJJ"
                        className="h-9 min-w-0 flex-1 rounded-md border border-slate-200 px-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                        value={entwurf}
                        onChange={event => {
                            setEntwurf(event.target.value); setEntwurfFehler('');
                            const iso = parseDeutschesDatum(event.target.value);
                            if (iso) { const ziel = parseDate(iso); if (ziel) { setView(ziel); setActiveDate(iso); setAnsicht('tage'); } }
                        }}
                        onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); uebernehmeEntwurf(); } }} />
                    <button type="button" className="shrink-0 rounded-md border border-rose-200 px-3 text-sm text-rose-700 hover:bg-rose-50 focus:outline-none focus:ring-2 focus:ring-rose-500"
                        onClick={uebernehmeEntwurf}>Übernehmen</button>
                </div>
                {entwurfFehler && <p role="alert" className="mt-1 text-xs text-rose-700">{entwurfFehler}</p>}
            </div>
            <div className="mb-3 flex items-center justify-between gap-1">
                <button type="button" title={zurueckLabel} aria-label={zurueckLabel} disabled={!canPrevious} onClick={zurueck} className="rounded p-2 hover:bg-rose-50 focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:opacity-30"><ChevronLeft aria-hidden="true" className="h-4 w-4" /></button>
                <button type="button" title={kopfLabel} aria-label={kopfLabel} aria-expanded={ansicht !== 'tage'}
                    onClick={() => setAnsicht(ansicht === 'tage' ? 'monate' : ansicht === 'monate' ? 'jahre' : 'monate')}
                    className="min-w-0 flex-1 rounded px-2 py-1 text-sm font-semibold hover:bg-rose-50 focus:outline-none focus:ring-2 focus:ring-rose-500">
                    <span aria-live="polite">{kopfText}</span>
                </button>
                <button type="button" title={vorLabel} aria-label={vorLabel} disabled={!canNext} onClick={vor} className="rounded p-2 hover:bg-rose-50 focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:opacity-30"><ChevronRight aria-hidden="true" className="h-4 w-4" /></button>
            </div>
            {ansicht === 'jahre' ? <div className="grid grid-cols-3 gap-1">
                {jahre.map(jahr => <button key={jahr} type="button" disabled={!jahrErlaubt(jahr)} aria-pressed={jahr === view.getFullYear()}
                    className={cn(gitterKlasse, jahr === view.getFullYear() ? 'bg-rose-600 text-white hover:bg-rose-700' : 'hover:bg-rose-50')}
                    onClick={() => { setView(new Date(jahr, view.getMonth(), 1)); setAnsicht('monate'); }}>{jahr}</button>)}
            </div> : ansicht === 'monate' ? <div className="grid grid-cols-3 gap-1">
                {MONTHS_KURZ.map((kurz, index) => <button key={kurz} type="button" disabled={!monatErlaubt(view.getFullYear(), index)}
                    aria-label={`${MONTHS[index]} ${view.getFullYear()}`} aria-pressed={index === view.getMonth()}
                    className={cn(gitterKlasse, index === view.getMonth() ? 'bg-rose-600 text-white hover:bg-rose-700' : 'hover:bg-rose-50')}
                    onClick={() => { setView(new Date(view.getFullYear(), index, 1)); setAnsicht('tage'); }}>{kurz}</button>)}
            </div> : <div className="grid grid-cols-7 gap-1">
                {WEEKDAYS.map(day => <span key={day} className="py-1 text-center text-xs text-slate-500">{day}</span>)}
                {Array.from({ length: first }, (_, index) => <span key={`empty-${index}`} />)}
                {Array.from({ length: days }, (_, index) => {
                    const date = new Date(view.getFullYear(), view.getMonth(), index + 1); const iso = isoDate(date);
                    return <button key={iso} data-date={iso} type="button" aria-label={displayDate(date)} aria-pressed={value === iso} disabled={!allowed(iso)} tabIndex={iso === focusDate ? 0 : -1}
                        className={cn('h-9 rounded-full text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:cursor-not-allowed disabled:text-slate-300', value === iso ? 'bg-rose-600 text-white hover:bg-rose-700' : 'hover:bg-rose-50', today === iso && value !== iso && 'border border-rose-300 text-rose-700')}
                        onClick={() => choose(iso)} onKeyDown={event => {
                            const offsets: Record<string, number> = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7, Home: -((date.getDay() + 6) % 7), End: 6 - ((date.getDay() + 6) % 7) };
                            // Bild auf/ab springt ganze Monate, mit Umschalt ganze Jahre --
                            // dieselbe Bedienung wie in nativen Kalendern.
                            if (event.key === 'PageUp' || event.key === 'PageDown') {
                                event.preventDefault();
                                const schritt = event.key === 'PageUp' ? -1 : 1;
                                const next = new Date(date);
                                if (event.shiftKey) next.setFullYear(date.getFullYear() + schritt); else next.setMonth(date.getMonth() + schritt);
                                const nextIso = isoDate(next); if (allowed(nextIso)) { setView(next); setActiveDate(nextIso); }
                                return;
                            }
                            if (offsets[event.key] === undefined) return;
                            event.preventDefault(); const next = new Date(date); next.setDate(date.getDate() + offsets[event.key]);
                            const nextIso = isoDate(next); if (allowed(nextIso)) { setView(next); setActiveDate(nextIso); }
                        }}>{index + 1}</button>;
                })}
            </div>}
            <div className="mt-3 flex justify-between gap-2 border-t border-slate-100 pt-2">
                <button type="button" disabled={!allowed(today)} onClick={() => choose(today)} className="rounded px-3 py-2 text-sm text-rose-700 hover:bg-rose-50 focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:opacity-40">Heute</button>
                <button type="button" onClick={() => choose('')} className="rounded px-3 py-2 text-sm text-slate-600 hover:bg-slate-100 focus:outline-none focus:ring-2 focus:ring-rose-500">Löschen</button>
                <button type="button" onClick={close} className="rounded px-3 py-2 text-sm text-slate-600 hover:bg-slate-100 focus:outline-none focus:ring-2 focus:ring-rose-500">Schließen</button>
            </div>
        </div>, document.body)}
    </div>;
}
