import { useState } from 'react';
import { CalendarClock, X } from 'lucide-react';
import { Button } from '../ui/button';
import { DatePicker } from '../ui/datepicker';

interface EmailValidityDialogProps {
    isOpen: boolean;
    onClose: () => void;
    onConfirm: (gueltigkeitTage: number) => void;
    /** Vorbelegung in Tagen (Default 14) */
    defaultTage?: number;
}

const QUICK_OPTIONS = [7, 14, 21, 30];

const formatDate = (date: Date): string =>
    date.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });

const tageBisDatum = (tage: number): Date => {
    const d = new Date();
    d.setHours(0, 0, 0, 0);
    d.setDate(d.getDate() + tage);
    return d;
};

const datumZuTage = (isoDate: string): number => {
    if (!isoDate) return 0;
    const ziel = new Date(isoDate);
    ziel.setHours(0, 0, 0, 0);
    const heute = new Date();
    heute.setHours(0, 0, 0, 0);
    const diffMs = ziel.getTime() - heute.getTime();
    return Math.max(1, Math.round(diffMs / (1000 * 60 * 60 * 24)));
};

export function EmailValidityDialog({ isOpen, onClose, onConfirm, defaultTage = 14 }: EmailValidityDialogProps) {
    const [tage, setTage] = useState<number>(defaultTage);
    const [customMode, setCustomMode] = useState<boolean>(false);
    const [customDatum, setCustomDatum] = useState<string>('');

    if (!isOpen) return null;

    const ablaufDatum = tageBisDatum(tage);
    const tageLabel = tage === 1 ? '1 Tag' : `${tage} Tage`;

    const handleQuick = (t: number) => {
        setTage(t);
        setCustomMode(false);
    };

    const handleCustomDatum = (iso: string) => {
        setCustomDatum(iso);
        if (iso) {
            const t = datumZuTage(iso);
            setTage(t);
        }
    };

    const handleConfirm = () => {
        onConfirm(tage);
    };

    return (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 backdrop-blur-sm">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-md mx-4 overflow-hidden" onClick={e => e.stopPropagation()}>
                {/* Header */}
                <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100 bg-rose-50/60">
                    <div className="flex items-center gap-3">
                        <div className="w-9 h-9 rounded-full bg-white border border-rose-200 flex items-center justify-center">
                            <CalendarClock className="w-4 h-4 text-rose-600" />
                        </div>
                        <h3 className="text-base font-semibold text-slate-900">Wie lange soll der Annahme-Link gültig sein?</h3>
                    </div>
                    <button onClick={onClose} className="text-slate-400 hover:text-slate-600 transition-colors" aria-label="Schließen">
                        <X className="w-4 h-4" />
                    </button>
                </div>

                {/* Content */}
                <div className="p-5 space-y-4">
                    <p className="text-sm text-slate-500">
                        Der Kunde kann das Angebot innerhalb dieses Zeitraums online ansehen und mit einem Klick verbindlich annehmen.
                    </p>

                    {/* Quick-Auswahl */}
                    <div className="grid grid-cols-2 gap-2">
                        {QUICK_OPTIONS.map(opt => {
                            const aktiv = !customMode && tage === opt;
                            return (
                                <button
                                    key={opt}
                                    type="button"
                                    onClick={() => handleQuick(opt)}
                                    className={
                                        'min-h-11 rounded-lg border px-4 py-2 text-sm font-medium transition-colors ' +
                                        (aktiv
                                            ? 'bg-rose-600 text-white border-rose-600'
                                            : 'bg-white text-slate-700 border-slate-200 hover:border-rose-300 hover:bg-rose-50/50')
                                    }
                                >
                                    {opt} Tage
                                </button>
                            );
                        })}
                    </div>

                    {/* Eigenes Datum */}
                    <div className="space-y-2">
                        <button
                            type="button"
                            onClick={() => setCustomMode(true)}
                            className={
                                'w-full text-left text-sm font-medium px-3 py-2 rounded-lg border transition-colors ' +
                                (customMode
                                    ? 'border-rose-300 bg-rose-50/50 text-rose-700'
                                    : 'border-slate-200 text-slate-600 hover:border-rose-300 hover:bg-rose-50/50')
                            }
                        >
                            Anderes Datum wählen…
                        </button>
                        {customMode && (
                            <DatePicker
                                value={customDatum}
                                onChange={handleCustomDatum}
                                placeholder="Gültig bis"
                            />
                        )}
                    </div>

                    {/* Zusammenfassung */}
                    <div className="rounded-lg bg-slate-50 border border-slate-200 px-4 py-3 text-sm">
                        <p className="text-slate-500">Der Link ist</p>
                        <p className="text-slate-900 font-semibold">
                            {tageLabel} gültig (bis zum {formatDate(ablaufDatum)})
                        </p>
                    </div>
                </div>

                {/* Footer */}
                <div className="flex justify-end items-center gap-2 px-5 py-3 border-t border-slate-100 bg-slate-50/50">
                    <Button variant="outline" size="sm" onClick={onClose}>
                        Abbrechen
                    </Button>
                    <Button
                        size="sm"
                        onClick={handleConfirm}
                        className="bg-rose-600 hover:bg-rose-700 text-white"
                    >
                        Weiter zum E-Mail-Versand
                    </Button>
                </div>
            </div>
        </div>
    );
}
