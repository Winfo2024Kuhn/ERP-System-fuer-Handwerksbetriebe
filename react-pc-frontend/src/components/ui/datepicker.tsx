import { useState, useRef, useEffect, useLayoutEffect, useId } from 'react';
import ReactDOM from 'react-dom';
import { Calendar, ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '../../lib/utils';

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
const WEEKDAYS = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
const isoDate = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
function parseDate(value: string): Date | null {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
    const [year, month, day] = value.split('-').map(Number);
    const date = new Date(0); date.setFullYear(year, month - 1, day); date.setHours(0, 0, 0, 0);
    return isoDate(date) === value ? date : null;
}
const displayDate = (date: Date) => date.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });

export function DatePicker({ value, onChange, placeholder = 'Datum wählen', className, disabled, id, name, required, min, max, error, 'aria-label': ariaLabel, 'aria-describedby': describedBy }: DatePickerProps) {
    const generatedId = useId(); const inputId = id ?? generatedId;
    const [isOpen, setIsOpen] = useState(false);
    const [view, setView] = useState(() => new Date());
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
        setView(parseDate(date) ?? new Date()); setActiveDate(date); setIsOpen(true);
    };
    useLayoutEffect(() => {
        if (!isOpen || disabled) return;
        const update = () => {
            const rect = triggerRef.current?.getBoundingClientRect(); if (!rect) return;
            const height = Math.min(400, Math.max(120, window.innerHeight - 16));
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
        if (isOpen && !disabled) popupRef.current?.querySelector<HTMLButtonElement>(`[data-date="${activeDate}"]:not(:disabled)`)?.focus();
    }, [isOpen, activeDate, disabled]);
    const days = new Date(view.getFullYear(), view.getMonth() + 1, 0).getDate();
    const first = (new Date(view.getFullYear(), view.getMonth(), 1).getDay() + 6) % 7;
    const today = isoDate(new Date());
    const firstVisible = isoDate(new Date(view.getFullYear(), view.getMonth(), 1));
    const lastVisible = isoDate(new Date(view.getFullYear(), view.getMonth(), days));
    const focusDate = activeDate >= firstVisible && activeDate <= lastVisible ? activeDate : (minValue && minValue > firstVisible && minValue <= lastVisible ? minValue : firstVisible);
    const navigate = (offset: number) => setView(new Date(view.getFullYear(), view.getMonth() + offset, 1));
    const canPrevious = !minValue || minValue < firstVisible;
    const canNext = !maxValue || maxValue > lastVisible;
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
                    const buttons = Array.from(popupRef.current?.querySelectorAll<HTMLButtonElement>('button:not(:disabled):not([tabindex="-1"])') ?? []);
                    if ((event.shiftKey && event.target === buttons[0]) || (!event.shiftKey && event.target === buttons.at(-1))) {
                        if (event.shiftKey) event.preventDefault(); close();
                    }
                }
            }}>
            <div className="mb-3 flex items-center justify-between">
                <button type="button" title="Vorheriger Monat" aria-label="Vorheriger Monat" disabled={!canPrevious} onClick={() => navigate(-1)} className="rounded p-2 hover:bg-rose-50 focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:opacity-30"><ChevronLeft aria-hidden="true" className="h-4 w-4" /></button>
                <span aria-live="polite" className="text-sm font-semibold">{MONTHS[view.getMonth()]} {view.getFullYear()}</span>
                <button type="button" title="Nächster Monat" aria-label="Nächster Monat" disabled={!canNext} onClick={() => navigate(1)} className="rounded p-2 hover:bg-rose-50 focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:opacity-30"><ChevronRight aria-hidden="true" className="h-4 w-4" /></button>
            </div>
            <div className="grid grid-cols-7 gap-1">
                {WEEKDAYS.map(day => <span key={day} className="py-1 text-center text-xs text-slate-500">{day}</span>)}
                {Array.from({ length: first }, (_, index) => <span key={`empty-${index}`} />)}
                {Array.from({ length: days }, (_, index) => {
                    const date = new Date(view.getFullYear(), view.getMonth(), index + 1); const iso = isoDate(date);
                    return <button key={iso} data-date={iso} type="button" aria-label={displayDate(date)} aria-pressed={value === iso} disabled={!allowed(iso)} tabIndex={iso === focusDate ? 0 : -1}
                        className={cn('h-9 rounded-full text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:cursor-not-allowed disabled:text-slate-300', value === iso ? 'bg-rose-600 text-white hover:bg-rose-700' : 'hover:bg-rose-50', today === iso && value !== iso && 'border border-rose-300 text-rose-700')}
                        onClick={() => choose(iso)} onKeyDown={event => {
                            const offsets: Record<string, number> = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7, Home: -((date.getDay() + 6) % 7), End: 6 - ((date.getDay() + 6) % 7) };
                            if (offsets[event.key] === undefined) return;
                            event.preventDefault(); const next = new Date(date); next.setDate(date.getDate() + offsets[event.key]);
                            const nextIso = isoDate(next); if (allowed(nextIso)) { setView(next); setActiveDate(nextIso); }
                        }}>{index + 1}</button>;
                })}
            </div>
            <div className="mt-3 flex justify-between gap-2 border-t border-slate-100 pt-2">
                <button type="button" disabled={!allowed(today)} onClick={() => choose(today)} className="rounded px-3 py-2 text-sm text-rose-700 hover:bg-rose-50 focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:opacity-40">Heute</button>
                <button type="button" onClick={() => choose('')} className="rounded px-3 py-2 text-sm text-slate-600 hover:bg-slate-100 focus:outline-none focus:ring-2 focus:ring-rose-500">Löschen</button>
                <button type="button" onClick={close} className="rounded px-3 py-2 text-sm text-slate-600 hover:bg-slate-100 focus:outline-none focus:ring-2 focus:ring-rose-500">Schließen</button>
            </div>
        </div>, document.body)}
    </div>;
}
