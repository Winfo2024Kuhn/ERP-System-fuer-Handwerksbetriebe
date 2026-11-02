import { useMemo, useState } from 'react';
import { CalendarClock, FolderPlus } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { ordnerKette, sichtbareZeilen, type Baum } from '../../lib/ordnerBaum';
import { OrdnerZeile, ZeilenRahmen, type OrdnerAktion } from './OrdnerZeile';
import { UEBERSICHT, useBaumTastatur } from './useBaumTastatur';
import type { FirmenOrdner } from './typen';

export interface DokumentOrdnerBaumProps {
    baum: Baum<FirmenOrdner>;
    /** `null` = „Übersicht“. */
    gewaehlt: number | null;
    /** Ordner mit Überfälligem (auch darunter) – bekommen den roten Punkt. */
    ueberfaellig: ReadonlySet<number>;
    onWaehlen: (id: number | null) => void;
    onNeuerOrdner: () => void;
    onAktion: (aktion: OrdnerAktion, ordner: FirmenOrdner) => void;
    onDateienAbgelegt: (ordnerId: number, dateien: FileList) => void;
}

/**
 * Ordnerbaum der Firmendokumente: oben „Übersicht“, darunter die Ordner zum
 * Auf- und Zuklappen, mit Anzahl, rotem Punkt bei Überfälligem und „…“-Menü.
 * PDFs lassen sich direkt auf einen Ordner ziehen.
 */
export function DokumentOrdnerBaum({ baum, gewaehlt, ueberfaellig, onWaehlen, onNeuerOrdner, onAktion, onDateienAbgelegt }: DokumentOrdnerBaumProps) {
    const [aufgeklappt, setAufgeklappt] = useState<Set<number>>(() => new Set());
    const [geprueft, setGeprueft] = useState<{ gewaehlt: number | null; baum: Baum<FirmenOrdner> } | null>(null);

    // Der gewählte Ordner muss sichtbar sein: seine übergeordneten Ordner aufklappen
    // (auch, wenn der Baum erst nach der Auswahl aus der Adresse ankommt).
    if (geprueft?.gewaehlt !== gewaehlt || geprueft.baum !== baum) {
        setGeprueft({ gewaehlt, baum });
        if (gewaehlt !== null) {
            const vorfahren = ordnerKette(baum, gewaehlt).slice(0, -1).map((o) => o.id);
            if (vorfahren.some((id) => !aufgeklappt.has(id))) setAufgeklappt(new Set([...aufgeklappt, ...vorfahren]));
        }
    }

    const zeilen = useMemo(() => sichtbareZeilen(baum, aufgeklappt), [baum, aufgeklappt]);
    const aufklappen = (id: number, offen: boolean) => setAufgeklappt((alt) => {
        const neu = new Set(alt);
        if (offen) neu.add(id); else neu.delete(id);
        return neu;
    });
    const tastatur = useBaumTastatur({ zeilen, gewaehlt, onWaehlen, onAufklappen: aufklappen });

    return (
        <nav aria-label="Ordner der Firmendokumente" className="flex min-h-0 flex-col">
            <div className="flex items-center justify-between gap-2 px-3 pb-2 pt-3">
                <h2 className="text-sm font-semibold text-slate-900">Ordner</h2>
                <Button size="sm" variant="outline" onClick={onNeuerOrdner}>
                    <FolderPlus className="h-4 w-4" aria-hidden="true" /> Neuer Ordner
                </Button>
            </div>
            <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-2">
                <div role="tree" aria-label="Ordner" className="space-y-0.5 p-0.5">
                    <ZeilenRahmen
                        ebene={0}
                        gewaehlt={gewaehlt === null}
                        aktiv={tastatur.aktiv === UEBERSICHT}
                        label="Übersicht"
                        refSetzen={tastatur.refSetzen(UEBERSICHT)}
                        onFokus={() => tastatur.setAktiv(UEBERSICHT)}
                        onKlick={() => onWaehlen(null)}
                        onTaste={(e) => tastatur.beiTaste(e, null)}
                    >
                        <CalendarClock className="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
                        <span className="flex-1">Übersicht</span>
                    </ZeilenRahmen>
                    {zeilen.map((zeile) => (
                        <OrdnerZeile
                            key={zeile.ordner.id}
                            zeile={zeile}
                            gewaehlt={gewaehlt === zeile.ordner.id}
                            aktiv={tastatur.aktiv === zeile.ordner.id}
                            ueberfaellig={ueberfaellig.has(zeile.ordner.id)}
                            refSetzen={tastatur.refSetzen(zeile.ordner.id)}
                            onFokus={() => tastatur.setAktiv(zeile.ordner.id)}
                            onWaehlen={() => onWaehlen(zeile.ordner.id)}
                            onTaste={(e) => tastatur.beiTaste(e, zeile)}
                            onAufklappen={(offen) => aufklappen(zeile.ordner.id, offen)}
                            onAktion={(aktion) => onAktion(aktion, zeile.ordner)}
                            onDateien={(dateien) => onDateienAbgelegt(zeile.ordner.id, dateien)}
                        />
                    ))}
                </div>
                {zeilen.length === 0 && <p className="px-3 py-4 text-sm text-slate-500">Noch keine Ordner.</p>}
            </div>
        </nav>
    );
}
