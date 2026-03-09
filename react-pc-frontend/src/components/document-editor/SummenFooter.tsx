import { useState, useRef, useEffect } from 'react';
import { formatCurrency } from './helpers';
import { Calendar, User, Hash, Building2, ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '../../lib/utils';

const MONTHS = [
    'Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
    'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'
];
const WEEKDAYS = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];

interface SummenFooterProps {
    nettosumme: number;
    blockCount: number;
    dokumentTypLabel: string;
    datum: string;
    kundennummer?: string;
    projektnummer?: string;
    betreff?: string;
    isLocked?: boolean;
    onDatumChange?: (value: string) => void;
    globalRabatt?: number;
    /** If true, the amounts shown are the effective rest (after deducting previous invoices) */
    istRestbetrag?: boolean;
}

export function SummenFooter({ nettosumme, blockCount, dokumentTypLabel, datum, kundennummer, projektnummer, betreff, isLocked, onDatumChange, globalRabatt, istRestbetrag }: SummenFooterProps) {
    const hasGlobalRabatt = (globalRabatt ?? 0) > 0;
    const rabattBetrag = hasGlobalRabatt ? nettosumme * (globalRabatt! / 100) : 0;
    const nettoNachRabatt = nettosumme - rabattBetrag;
    const mwstBetrag = nettoNachRabatt * 0.19;
    const bruttosumme = nettoNachRabatt + mwstBetrag;
    const formattedDatum = datum ? new Date(datum).toLocaleDateString('de-DE') : '–';

    // Inline calendar state (opens upward)
    const [calOpen, setCalOpen] = useState(false);
    const selectedDate = datum ? new Date(datum) : null;
    const [curMonth, setCurMonth] = useState(() => selectedDate ? new Date(selectedDate.getFullYear(), selectedDate.getMonth(), 1) : new Date());
    const calRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (selectedDate) setCurMonth(new Date(selectedDate.getFullYear(), selectedDate.getMonth(), 1));
    }, [datum]);

    useEffect(() => {
        const handler = (e: MouseEvent) => {
            if (calRef.current && !calRef.current.contains(e.target as Node)) setCalOpen(false);
        };
        document.addEventListener('mousedown', handler);
        return () => document.removeEventListener('mousedown', handler);
    }, []);

    const daysInMonth = new Date(curMonth.getFullYear(), curMonth.getMonth() + 1, 0).getDate();
    const firstDay = (() => { const d = new Date(curMonth.getFullYear(), curMonth.getMonth(), 1).getDay(); return d === 0 ? 6 : d - 1; })();

    const handleSelect = (day: number) => {
        const y = curMonth.getFullYear();
        const m = String(curMonth.getMonth() + 1).padStart(2, '0');
        const d = String(day).padStart(2, '0');
        onDatumChange?.(`${y}-${m}-${d}`);
        setCalOpen(false);
    };

    const isToday = (day: number) => { const t = new Date(); return day === t.getDate() && curMonth.getMonth() === t.getMonth() && curMonth.getFullYear() === t.getFullYear(); };
    const isSel = (day: number) => selectedDate ? day === selectedDate.getDate() && curMonth.getMonth() === selectedDate.getMonth() && curMonth.getFullYear() === selectedDate.getFullYear() : false;

    const canEditDatum = !isLocked && !!onDatumChange;

    return (
        <div className="bg-white border-t border-slate-200 px-4 h-9 flex items-center justify-between text-[11px] flex-shrink-0">
            {/* Left: document info + Kopfdaten */}
            <div className="flex items-center gap-2.5 text-slate-400 min-w-0 overflow-visible">
                <span className="font-semibold text-rose-600 bg-rose-50 px-1.5 py-0.5 rounded text-[10px] flex-shrink-0">
                    {dokumentTypLabel}
                </span>
                <span className="text-slate-300">|</span>
                <div className="relative flex-shrink-0" ref={calRef}>
                    <button
                        type="button"
                        onClick={() => canEditDatum && setCalOpen(!calOpen)}
                        className={cn(
                            "flex items-center gap-1",
                            canEditDatum && "hover:text-rose-600 cursor-pointer transition-colors"
                        )}
                    >
                        <Calendar className="w-3 h-3" />
                        <span>{formattedDatum}</span>
                    </button>

                    {calOpen && (
                        <div className="absolute bottom-full left-0 mb-2 w-72 rounded-md border border-slate-200 bg-white p-3 shadow-lg z-50 animate-in fade-in-0 zoom-in-95">
                            <div className="flex items-center justify-between mb-3">
                                <div className="flex items-center gap-1">
                                    <button type="button" onClick={() => setCurMonth(new Date(curMonth.getFullYear() - 1, curMonth.getMonth(), 1))} className="p-1 rounded hover:bg-slate-100 transition-colors text-slate-400 hover:text-slate-600" title="Vorheriges Jahr">
                                        <ChevronLeft className="h-3 w-3" /><ChevronLeft className="h-3 w-3 -ml-2" />
                                    </button>
                                    <button type="button" onClick={() => setCurMonth(new Date(curMonth.getFullYear(), curMonth.getMonth() - 1, 1))} className="p-1 rounded hover:bg-slate-100 transition-colors" title="Vorheriger Monat">
                                        <ChevronLeft className="h-4 w-4" />
                                    </button>
                                </div>
                                <button type="button" onClick={() => setCurMonth(new Date())} className="font-semibold text-sm text-slate-900 hover:text-rose-600 transition-colors" title="Zum aktuellen Monat">
                                    {MONTHS[curMonth.getMonth()]} {curMonth.getFullYear()}
                                </button>
                                <div className="flex items-center gap-1">
                                    <button type="button" onClick={() => setCurMonth(new Date(curMonth.getFullYear(), curMonth.getMonth() + 1, 1))} className="p-1 rounded hover:bg-slate-100 transition-colors" title="Nächster Monat">
                                        <ChevronRight className="h-4 w-4" />
                                    </button>
                                    <button type="button" onClick={() => setCurMonth(new Date(curMonth.getFullYear() + 1, curMonth.getMonth(), 1))} className="p-1 rounded hover:bg-slate-100 transition-colors text-slate-400 hover:text-slate-600" title="Nächstes Jahr">
                                        <ChevronRight className="h-3 w-3" /><ChevronRight className="h-3 w-3 -ml-2" />
                                    </button>
                                </div>
                            </div>
                            <div className="grid grid-cols-7 gap-1 mb-1">
                                {WEEKDAYS.map(d => <div key={d} className="h-8 w-8 flex items-center justify-center text-xs font-medium text-slate-500">{d}</div>)}
                            </div>
                            <div className="grid grid-cols-7 gap-1">
                                {Array.from({ length: firstDay }, (_, i) => <div key={`e${i}`} className="h-8 w-8" />)}
                                {Array.from({ length: daysInMonth }, (_, i) => {
                                    const day = i + 1;
                                    return (
                                        <button key={day} type="button" onClick={() => handleSelect(day)}
                                            className={cn("h-8 w-8 rounded-full text-sm font-medium transition-colors hover:bg-rose-100 hover:text-rose-700",
                                                isToday(day) && !isSel(day) && "border border-rose-300 text-rose-600",
                                                isSel(day) && "bg-rose-600 text-white hover:bg-rose-700 hover:text-white"
                                            )}>{day}</button>
                                    );
                                })}
                            </div>
                            <div className="mt-3 pt-2 border-t border-slate-100 flex justify-end">
                                <button type="button" onClick={() => { const t = new Date(); onDatumChange?.(`${t.getFullYear()}-${String(t.getMonth()+1).padStart(2,'0')}-${String(t.getDate()).padStart(2,'0')}`); setCalOpen(false); }} className="text-xs text-rose-600 hover:text-rose-700 font-medium">Heute</button>
                            </div>
                        </div>
                    )}
                </div>
                {kundennummer && (
                    <>
                        <span className="text-slate-300">|</span>
                        <div className="flex items-center gap-1 flex-shrink-0">
                            <User className="w-3 h-3" />
                            <span>KD {kundennummer}</span>
                        </div>
                    </>
                )}
                {projektnummer && (
                    <>
                        <span className="text-slate-300">|</span>
                        <div className="flex items-center gap-1 flex-shrink-0">
                            <Hash className="w-3 h-3" />
                            <span>Projektnummer {projektnummer}</span>
                        </div>
                    </>
                )}
                {betreff && (
                    <>
                        <span className="text-slate-300">|</span>
                        <div className="flex items-center gap-1 min-w-0">
                            <Building2 className="w-3 h-3 flex-shrink-0" />
                            <span className="truncate max-w-[200px]">{betreff}</span>
                        </div>
                    </>
                )}
                <span className="text-slate-300">|</span>
                <span className="flex-shrink-0">{blockCount} {blockCount === 1 ? 'Block' : 'Blöcke'}</span>
            </div>

            {/* Right: summen */}
            <div className="flex items-center gap-4 flex-shrink-0">
                <div className="flex items-center gap-1.5">
                    <span className="text-slate-400">Netto{istRestbetrag ? ' (Rest)' : ''}:</span>
                    <span className="font-medium text-slate-600">{formatCurrency(nettosumme)} €</span>
                </div>
                {hasGlobalRabatt && (
                    <div className="flex items-center gap-1.5">
                        <span className="text-rose-500 text-[10px] font-semibold">−{globalRabatt}%</span>
                        <span className="text-slate-300">→</span>
                        <span className="font-medium text-slate-600">{formatCurrency(nettoNachRabatt)} €</span>
                    </div>
                )}
                <div className="flex items-center gap-1.5">
                    <span className="text-slate-400">MwSt:</span>
                    <span className="text-slate-500">{formatCurrency(mwstBetrag)} €</span>
                </div>
                <div className="h-3 w-px bg-slate-200" />
                <div className="flex items-center gap-1.5">
                    <span className="font-bold text-slate-700">Brutto:</span>
                    <span className="font-bold text-rose-600">{formatCurrency(bruttosumme)} €</span>
                </div>
            </div>
        </div>
    );
}
