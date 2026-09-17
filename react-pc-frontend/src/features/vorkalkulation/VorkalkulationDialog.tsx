import { useCallback, useEffect, useState } from 'react';
import { ChevronLeft, ChevronRight, Scale } from 'lucide-react';

import { Button } from '../../components/ui/button';
import { Dialog, DialogContent } from '../../components/ui/dialog';
import { useToast } from '../../components/ui/toast';
import { formatCurrency } from '../../components/artikel/formatCurrency';
import { cn } from '../../lib/utils';

import { berechneGesamt, pruefeEntwuerfe } from './berechnung';
import { MaterialSchritt } from './schritte/MaterialSchritt';
import { ArbeitszeitSchritt } from './schritte/ArbeitszeitSchritt';
import { GesamtSchritt } from './schritte/GesamtSchritt';
import type { VorkalkulationDaten } from './types';

type Schritt = 'material' | 'arbeitszeit' | 'gesamt';

const SCHRITTE: { name: Schritt; label: string }[] = [
    { name: 'material', label: 'Material' },
    { name: 'arbeitszeit', label: 'Arbeitszeit' },
    { name: 'gesamt', label: 'Gesamtkalkulation' },
];

export interface VorkalkulationDialogProps {
    offen: boolean;
    /** Titel der Leistung, an der die Kalkulation haengt. */
    leistungTitel: string;
    positionsnummer: string;
    startdaten: VorkalkulationDaten;
    /** Nur lesen — z. B. wenn das Dokument gesperrt ist. */
    readOnly?: boolean;
    onSchliessen: () => void;
    /** Uebergibt den fertigen Verkaufspreis an die Leistungs-Position. */
    onPreisUebernehmen: (preis: number, daten: VorkalkulationDaten) => void;
}

/**
 * Vollbild-Fenster der Vor-Kalkulation.
 *
 * Bewusst der gemeinsame `Dialog` mit Vollbild-Maßen statt eines eigenen
 * `fixed inset-0`-Overlays: Der Artikel-Picker im Material-Reiter ist selbst
 * ein `Dialog`. Nur als `Dialog` erbt er ueber `DialogDepth` die naechsthoehere
 * Ebene und liegt damit VOR diesem Fenster; ein eigenes Overlay mit fester
 * z-Ebene haette ihn verdeckt und ausserdem Escape und den Tab-Fokus an sich
 * gerissen. Der gemeinsame Dialog bringt zudem Hintergrund-Sperre und die
 * Tastatur-Erreichbarkeit der Meldungen mit (FRONTEND_UI.md).
 *
 * Die laufende Summe steht immer in der Fussleiste, auch auf dem Material-
 * Reiter. So sieht der Bediener bei jeder Aenderung sofort, was sie mit dem
 * Preis macht, statt bis zum letzten Reiter blaettern zu muessen.
 */
export function VorkalkulationDialog({
    offen,
    leistungTitel,
    positionsnummer,
    startdaten,
    readOnly = false,
    onSchliessen,
    onPreisUebernehmen,
}: VorkalkulationDialogProps) {
    const toast = useToast();
    const [daten, setDaten] = useState<VorkalkulationDaten>(startdaten);
    const [schritt, setSchritt] = useState<Schritt>('material');

    // Beim Oeffnen die uebergebenen Daten uebernehmen und vorne anfangen.
    useEffect(() => {
        if (offen) {
            setDaten(startdaten);
            setSchritt('material');
        }
        // startdaten bewusst nicht in den Abhaengigkeiten: Ein neues Objekt bei
        // jedem Render der Elternkomponente wuerde sonst die laufende Eingabe
        // bei jedem Tastendruck zuruecksetzen.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [offen]);

    const aendern = useCallback((patch: Partial<VorkalkulationDaten>) => {
        setDaten((alt) => ({ ...alt, ...patch }));
    }, []);

    const uebernehmen = () => {
        // Vor der Aktion vollstaendig pruefen — ein halb getippter oder leerer
        // Wert darf nicht still als 0 in den Angebotspreis wandern.
        const fehler = pruefeEntwuerfe(daten);
        if (fehler) {
            toast.error(fehler.meldung);
            return;
        }
        const ergebnis = berechneGesamt(daten);
        if (ergebnis.verkaufspreisNetto <= 0) {
            toast.error('Die Kalkulation ergibt noch keinen Preis. Bitte Material oder Arbeitszeit eintragen.');
            return;
        }
        onPreisUebernehmen(ergebnis.verkaufspreisNetto, daten);
    };

    if (!offen) return null;

    const ergebnis = berechneGesamt(daten);
    const index = SCHRITTE.findIndex((s) => s.name === schritt);

    const schrittleiste = (klein?: boolean) => (
        <ol className="flex items-center gap-2 text-sm">
            {SCHRITTE.map((s, i) => (
                <li key={s.name} className="flex items-center gap-2">
                    {i > 0 && <span className="text-slate-300" aria-hidden="true">/</span>}
                    <button
                        type="button"
                        onClick={() => setSchritt(s.name)}
                        aria-current={schritt === s.name ? 'step' : undefined}
                        className={cn(
                            'rounded-md px-2 py-1 transition-colors',
                            schritt === s.name
                                ? 'font-semibold text-rose-700'
                                : 'text-slate-400 hover:bg-slate-50 hover:text-slate-600',
                            klein && 'px-1 py-0',
                        )}
                    >
                        {s.label}
                    </button>
                </li>
            ))}
        </ol>
    );

    return (
        <Dialog
            open={offen}
            onOpenChange={(nunOffen) => { if (!nunOffen) onSchliessen(); }}
            aria-label={`Vor-Kalkulation für Position ${positionsnummer}`}
            className="h-[94vh] w-[97vw] max-w-none overflow-hidden rounded-2xl p-0"
        >
            <DialogContent className="gap-0">
                {/* ── Kopfzeile ─────────────────────────────────────────── */}
                {/* pr-14 haelt die Ecke fuer den Schliessen-Knopf des Dialogs frei. */}
                <div className="flex flex-shrink-0 items-start justify-between gap-4 border-b border-slate-200 px-6 py-4 pr-14">
                    <div className="min-w-0">
                        <p className="text-sm font-semibold uppercase tracking-wide text-rose-600">
                            Vor-Kalkulation
                        </p>
                        <h2 className="truncate text-xl font-bold text-slate-900">
                            Pos. {positionsnummer} · {leistungTitel || 'Ohne Titel'}
                        </h2>
                        {readOnly && (
                            <p className="mt-0.5 text-xs text-amber-700">
                                Nur Ansicht — das Dokument ist gerade von jemand anderem in Bearbeitung.
                            </p>
                        )}
                    </div>

                    <nav aria-label="Schritte der Kalkulation" className="hidden flex-shrink-0 md:block">
                        {schrittleiste()}
                    </nav>
                </div>

                {/* Schrittleiste auf schmalen Bildschirmen */}
                <nav
                    aria-label="Schritte der Kalkulation"
                    className="flex-shrink-0 border-b border-slate-200 px-6 py-2 md:hidden"
                >
                    {schrittleiste(true)}
                </nav>

                {/* ── Inhalt ────────────────────────────────────────────── */}
                <div className="min-h-0 flex-1 overflow-y-auto bg-slate-50 px-6 py-5">
                    <div className="mx-auto max-w-[1400px]">
                        {schritt === 'material' && (
                            <MaterialSchritt daten={daten} aendern={aendern} readOnly={readOnly} />
                        )}
                        {schritt === 'arbeitszeit' && (
                            <ArbeitszeitSchritt daten={daten} aendern={aendern} readOnly={readOnly} />
                        )}
                        {schritt === 'gesamt' && (
                            <GesamtSchritt
                                daten={daten}
                                aendern={aendern}
                                readOnly={readOnly}
                                onPreisUebernehmen={uebernehmen}
                            />
                        )}
                    </div>
                </div>

                {/* ── Fußleiste mit laufender Summe ─────────────────────── */}
                <div className="flex flex-shrink-0 flex-wrap items-center gap-4 border-t border-slate-200 bg-white px-6 py-3">
                    <div className="flex items-center gap-2 text-slate-500">
                        <Scale className="h-4 w-4 text-rose-500" aria-hidden="true" />
                        <span className="text-xs font-semibold uppercase tracking-wider">Stand</span>
                    </div>
                    <div className="flex flex-wrap items-center gap-x-5 gap-y-1">
                        <span className="text-sm text-slate-600">
                            Kostet uns{' '}
                            <strong className="tabular-nums text-slate-900">
                                {formatCurrency(ergebnis.herstellkosten)}
                            </strong>
                        </span>
                        <span className="text-sm text-slate-600">
                            Wir nehmen{' '}
                            <strong className="tabular-nums text-rose-700">
                                {formatCurrency(ergebnis.verkaufspreisNetto)}
                            </strong>
                        </span>
                    </div>

                    <div className="ml-auto flex items-center gap-2">
                        <Button
                            variant="outline"
                            size="sm"
                            disabled={index === 0}
                            onClick={() => setSchritt(SCHRITTE[Math.max(0, index - 1)].name)}
                        >
                            <ChevronLeft className="h-4 w-4" aria-hidden="true" />
                            Zurück
                        </Button>
                        {index < SCHRITTE.length - 1 ? (
                            <Button size="sm" onClick={() => setSchritt(SCHRITTE[index + 1].name)}>
                                Weiter
                                <ChevronRight className="h-4 w-4" aria-hidden="true" />
                            </Button>
                        ) : (
                            <Button size="sm" onClick={uebernehmen} disabled={readOnly}>
                                Preis übernehmen
                            </Button>
                        )}
                    </div>
                </div>
            </DialogContent>
        </Dialog>
    );
}
