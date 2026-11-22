import { GraduationCap, Plane, RefreshCw, Stethoscope, Trash2 } from 'lucide-react';
import type { AbwesenheitTyp, Buchung } from './kalenderTypen';
import { loeschKnopfName } from './buchungLoeschen';

const DARSTELLUNG = {
    URLAUB: { label: 'Urlaub', Icon: Plane, card: 'bg-green-50 border-green-200', iconBox: 'bg-green-100 text-green-600', text: 'text-green-800' },
    KRANKHEIT: { label: 'Krankheit', Icon: Stethoscope, card: 'bg-red-50 border-red-200', iconBox: 'bg-red-100 text-red-600', text: 'text-red-800' },
    FORTBILDUNG: { label: 'Fortbildung', Icon: GraduationCap, card: 'bg-blue-50 border-blue-200', iconBox: 'bg-blue-100 text-blue-600', text: 'text-blue-800' },
    ZEITAUSGLEICH: { label: 'Zeitausgleich', Icon: RefreshCw, card: 'bg-amber-50 border-amber-200', iconBox: 'bg-amber-100 text-amber-600', text: 'text-amber-800' },
} as const;

interface Props {
    buchung: Buchung & { typ: AbwesenheitTyp };
    readOnly: boolean;
    onLoeschen: () => void;
}

/** Kompakte Karte für Urlaub, Krankheit, Fortbildung, Zeitausgleich – ohne Projekt und Tätigkeit. */
export function AbwesenheitKarte({ buchung, readOnly, onLoeschen }: Props) {
    const cfg = DARSTELLUNG[buchung.typ];
    const Icon = cfg.Icon;
    const stunden = buchung.dauerMinuten != null ? buchung.dauerMinuten / 60 : null;
    return (
        <div className={`rounded-lg border shadow-sm p-4 ${cfg.card}`}>
            <div className="flex items-center gap-4">
                <div className={`p-3 rounded-xl flex-shrink-0 ${cfg.iconBox}`}>
                    <Icon className="w-6 h-6" />
                </div>
                <div className="flex-1 min-w-0">
                    <p className={`font-semibold ${cfg.text}`}>{cfg.label}</p>
                    <p className="text-sm text-slate-500">
                        {stunden != null ? `${stunden.toLocaleString('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} Std. angerechnet` : 'Ganzer Tag'}
                    </p>
                    {buchung.notiz && <p className="text-xs text-slate-400 mt-0.5 truncate" title={buchung.notiz}>{buchung.notiz}</p>}
                </div>
                {!readOnly && (
                    <button
                        type="button"
                        onClick={onLoeschen}
                        aria-label={loeschKnopfName(buchung)}
                        className="p-2 bg-white/60 text-slate-400 rounded hover:bg-white hover:text-red-500 transition-colors flex-shrink-0"
                        title="Entfernen"
                    >
                        <Trash2 className="w-5 h-5" />
                    </button>
                )}
            </div>
        </div>
    );
}
