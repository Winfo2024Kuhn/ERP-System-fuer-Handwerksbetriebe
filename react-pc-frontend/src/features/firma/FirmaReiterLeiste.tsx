import type { ReactNode } from 'react';
import { Building2, HeartPulse, ShieldCheck, Users, type LucideIcon } from 'lucide-react';
import { cn } from '../../lib/utils';
import type { FirmaReiter } from './firmaTypen';

interface Props {
    aktiv: FirmaReiter;
    onWechsel: (reiter: FirmaReiter) => void;
    steuerberaterAnzahl: number;
}

const REITER: { id: FirmaReiter; icon: LucideIcon; text: (anzahl: number) => ReactNode }[] = [
    { id: 'firma', icon: Building2, text: () => 'Firmendaten' },
    { id: 'lohn-stammdaten', icon: HeartPulse, text: () => 'Lohn-Stammdaten' },
    { id: 'unfallversicherung', icon: ShieldCheck, text: () => 'Unfallversicherung' },
    // JSX statt Template-String: gleiche Textknoten wie bisher, damit die Schrift identisch läuft
    { id: 'steuerberater', icon: Users, text: anzahl => <>Steuerberater ({anzahl})</> },
    { id: 'steuerpruefung', icon: ShieldCheck, text: () => 'Steuerprüfung' },
];

/** Hauptreiter der Firma-Seite. */
export function FirmaReiterLeiste({ aktiv, onWechsel, steuerberaterAnzahl }: Props) {
    return (
        <div className="flex gap-2 mb-6 border-b border-slate-200 pb-2">
            {REITER.map(({ id, icon: Icon, text }) => (
                <button
                    key={id}
                    onClick={() => onWechsel(id)}
                    className={cn(
                        "px-4 py-2 text-sm font-medium rounded-t-lg transition",
                        aktiv === id
                            ? "bg-rose-50 text-rose-700 border-b-2 border-rose-600"
                            : "text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                    )}
                >
                    <Icon className="w-4 h-4 inline-block mr-2" />
                    {text(steuerberaterAnzahl)}
                </button>
            ))}
        </div>
    );
}
