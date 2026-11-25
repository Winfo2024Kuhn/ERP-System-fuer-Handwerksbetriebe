import { AlertTriangle, FileText, Mail, Phone, StickyNote, Users, type LucideIcon } from 'lucide-react';
import { cn } from '../../lib/utils';
import type { LieferantTab } from './lieferantHilfen';

/** Zahl am Reiter; fehlt sie, zeigt der Reiter keinen Zähler. */
export type ReiterZaehler = Partial<Record<LieferantTab, number>>;

const REITER: { id: LieferantTab; icon: LucideIcon; text: string }[] = [
    { id: 'emails', icon: Mail, text: 'E-Mail-Verlauf' },
    { id: 'ansprechpartner', icon: Users, text: 'Ansprechpartner' },
    { id: 'anrufe', icon: Phone, text: 'Anrufe' },
    { id: 'dokumente', icon: FileText, text: 'Dokumente' },
    { id: 'notizen', icon: StickyNote, text: 'Notizen' },
    { id: 'reklamationen', icon: AlertTriangle, text: 'Reklamationen' },
];

interface Props {
    aktiv: LieferantTab;
    onWechsel: (tab: LieferantTab) => void;
    /** Ohne Telefon-Recht gibt es keinen Reiter „Anrufe“. */
    mitAnrufen: boolean;
    zaehler: ReiterZaehler;
}

/**
 * Reiterleiste der Lieferanten-Detailansicht. Der offene Reiter steht in der URL,
 * nicht im lokalen State. flex-wrap + min-w-0 statt overflow-x-auto: eine versteckt
 * scrollende Reiterleiste ist keine Loesung, lieber umbrechen lassen.
 */
export function LieferantReiterLeiste({ aktiv, onWechsel, mitAnrufen, zaehler }: Props) {
    return (
        <div role="tablist" aria-label="Bereiche des Lieferanten" className="flex items-center gap-1 mb-6 border-b border-slate-200 flex-wrap min-w-0">
            {REITER.filter(r => mitAnrufen || r.id !== 'anrufe').map(({ id, icon: Icon, text }) => {
                const anzahl = zaehler[id];
                return (
                    <button
                        key={id}
                        role="tab"
                        aria-selected={aktiv === id}
                        onClick={() => onWechsel(id)}
                        className={cn(
                            "flex items-center gap-2 px-3 py-2.5 text-sm font-medium transition-colors -mb-px",
                            aktiv === id
                                ? "text-rose-600 border-b-2 border-rose-500"
                                : "text-slate-500 hover:text-slate-700"
                        )}
                    >
                        <Icon className="w-4 h-4" />
                        {text}
                        {anzahl !== undefined && (
                            <span className="text-xs bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded-full tabular-nums">
                                {anzahl}
                            </span>
                        )}
                    </button>
                );
            })}
        </div>
    );
}
