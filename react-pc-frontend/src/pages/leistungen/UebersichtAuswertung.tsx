import { BarChart3 } from 'lucide-react';
import { AuswertungTabelle } from './AuswertungTabelle';
import { Fehlerkarte } from './AuswertungReiter';
import { JahrAuswahl } from './JahrAuswahl';
import { useAuswertung } from './useAuswertung';

export interface UebersichtAuswertungProps {
    jahr: string;
    onJahr: (jahr: string) => void;
    /** Führt in die Auswertung des Ordners. */
    onWaehlen: (id: number) => void;
    keineOrdner: boolean;
}

/** Übersicht aller Hauptordner: Projekte, Stunden, Menge, Stunden je Einheit, Leistungen. */
export function UebersichtAuswertung({ jahr, onJahr, onWaehlen, keineOrdner }: UebersichtAuswertungProps) {
    const { zeilen, laedt, fehler, erneut } = useAuswertung(jahr, null, !keineOrdner);
    const ohneProjekte = zeilen !== null && zeilen.every((z) => z.projektAnzahl === 0);

    if (keineOrdner) {
        return (
            <div className="rounded-lg border border-dashed border-slate-200 p-10 text-center">
                <BarChart3 className="mx-auto mb-2 h-10 w-10 text-rose-200" aria-hidden="true" />
                <p className="font-medium text-slate-700">Noch keine Ordner angelegt.</p>
                <p className="mt-1 text-sm text-slate-500">Legen Sie links mit „+ Ordner“ den ersten an – z. B. „Metallbau“ oder „Treppen“.</p>
            </div>
        );
    }

    return (
        <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <JahrAuswahl jahr={jahr} onJahr={onJahr} />
                <p className="text-xs text-slate-500">Nur abgeschlossene Projekte · Zeile anklicken für Details</p>
            </div>
            {laedt && <div role="status" aria-label="Übersicht wird geladen" className="h-48 rounded-lg bg-slate-100 motion-safe:animate-pulse" />}
            {!laedt && fehler && <Fehlerkarte text={fehler} onErneut={erneut} />}
            {!laedt && zeilen && (
                <>
                    {ohneProjekte && (
                        <p className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-600">
                            Noch keine abgeschlossenen Projekte mit Zeiten{jahr ? ` im Jahr ${jahr}` : ''}. Stunden und Richtwerte erscheinen, sobald Projekte abgeschlossen sind.
                        </p>
                    )}
                    <AuswertungTabelle zeilen={zeilen} beschriftung="Auswertung aller Hauptordner" onWaehlen={onWaehlen} />
                </>
            )}
        </div>
    );
}
