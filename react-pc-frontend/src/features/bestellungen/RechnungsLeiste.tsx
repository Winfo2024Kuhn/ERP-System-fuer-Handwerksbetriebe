import { useEffect, useRef } from 'react';
import { CheckCircle2, CircleDashed, EyeOff, Package } from 'lucide-react';
import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';
import { cn } from '../../lib/utils';
import { parseIsoDatum } from './bestellungenListe';
import { istOffeneRechnung, rechnungsStatus, type LeistenDokument, type RechnungsStatus } from './rechnungsLeisteLogik';

const STATUS: Record<RechnungsStatus, { text: string; Icon: typeof CheckCircle2; klasse: string }> = {
    offen: { text: 'offen', Icon: CircleDashed, klasse: 'text-amber-700' },
    zugeordnet: { text: 'zugeordnet', Icon: CheckCircle2, klasse: 'text-emerald-700' },
    lager: { text: 'Lager', Icon: Package, klasse: 'text-slate-600' },
    ausgeblendet: { text: 'ausgeblendet', Icon: EyeOff, klasse: 'text-slate-500' },
};

const formatDatum = (dokument: LeistenDokument): string => {
    const datum = parseIsoDatum(dokument.dokumentDatum) ?? parseIsoDatum(dokument.eingangsDatum);
    return datum ? datum.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' }) : '–';
};

const formatBetrag = (dokument: LeistenDokument): string => {
    const betrag = dokument.betragBrutto ?? dokument.betragNetto ?? null;
    if (betrag == null || !Number.isFinite(betrag)) return '– €';
    return `${betrag.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} €`;
};

interface RechnungsLeisteProps {
    rechnungen: LeistenDokument[];
    aktivId: number;
    onWaehlen: (id: number) => void;
    /** Nach einem Wechsel per Leiste den Fokus wieder auf die gewählte Rechnung setzen (Tastatur). */
    autoFokus?: boolean;
}

/**
 * Leiste oben im Zuordnungsdialog, wenn eine Bestellung mehrere Rechnungen hat:
 * je Rechnung ein Knopf mit Nummer, Datum, Betrag und Stand. Ein Klick wechselt
 * die Rechnung im Dialog.
 */
export function RechnungsLeiste({ rechnungen, aktivId, onWaehlen, autoFokus }: RechnungsLeisteProps) {
    const aktivRef = useRef<HTMLButtonElement>(null);
    const offen = rechnungen.filter(istOffeneRechnung).length;

    useEffect(() => {
        if (autoFokus) aktivRef.current?.focus();
    }, [autoFokus]);

    return (
        <div className="flex items-center gap-3 border-b border-slate-200 bg-white px-6 py-2">
            <div className="flex shrink-0 items-center gap-1 text-sm font-medium text-slate-700">
                <span>Rechnungen dieser Bestellung</span>
                <InfoButton titel="Mehrere Rechnungen zu einer Bestellung">
                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                        <p>
                            Zu einer Bestellung können mehrere Rechnungen gehören, zum Beispiel Teilrechnungen. Jede Rechnung
                            wird für sich Projekten oder Kostenstellen zugeordnet.
                        </p>
                    </InfoAbschnitt>
                    <InfoAbschnitt titel="Was bewirkt das?">
                        <p>
                            Die Bestellung wechselt erst in den Reiter ‚Erledigt‘, wenn jede Rechnung zugeordnet oder als
                            Lagerbestellung markiert ist. Schon zugeordnete Rechnungen können Sie hier ansehen und ändern.
                        </p>
                    </InfoAbschnitt>
                </InfoButton>
                <span className={cn(
                    'ml-1 rounded-full px-2 py-0.5 text-xs font-medium',
                    offen > 0 ? 'bg-amber-100 text-amber-800' : 'bg-emerald-100 text-emerald-800',
                )}>
                    {offen > 0 ? `noch ${offen} offen` : 'alle erledigt'}
                </span>
            </div>
            {/* px-1 py-1: Fokus-Ringe der Knöpfe am Scrollrand nicht abschneiden */}
            <div role="group" aria-label="Rechnungen dieser Bestellung" className="-my-1 flex min-w-0 flex-1 gap-2 overflow-x-auto px-1 py-1">
                {rechnungen.map(rechnung => {
                    const aktiv = rechnung.id === aktivId;
                    const status = rechnungsStatus(rechnung);
                    const { text, Icon, klasse } = STATUS[status];
                    return (
                        <button
                            key={rechnung.id}
                            ref={aktiv ? aktivRef : undefined}
                            type="button"
                            aria-pressed={aktiv}
                            onClick={() => { if (!aktiv) onWaehlen(rechnung.id); }}
                            className={cn(
                                'inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full border px-3 py-1 text-xs transition-colors',
                                'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500',
                                aktiv
                                    ? 'border-rose-400 bg-rose-50 font-medium text-rose-800'
                                    : 'border-slate-200 bg-white text-slate-700 hover:border-rose-200 hover:bg-rose-50/50',
                                status === 'ausgeblendet' && !aktiv && 'opacity-60',
                            )}
                        >
                            <span>{rechnung.dokumentNummer || rechnung.dateiname}</span>
                            <span aria-hidden="true" className="text-slate-300">·</span>
                            <span className="tabular-nums">{formatDatum(rechnung)}</span>
                            <span aria-hidden="true" className="text-slate-300">·</span>
                            <span className="tabular-nums">{formatBetrag(rechnung)}</span>
                            <span aria-hidden="true" className="text-slate-300">·</span>
                            <span className={cn('inline-flex items-center gap-1', klasse)}>
                                <Icon className="h-3.5 w-3.5" aria-hidden="true" />
                                {text}
                            </span>
                        </button>
                    );
                })}
            </div>
        </div>
    );
}
