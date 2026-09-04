import { Check, Pencil, WifiOff } from 'lucide-react';
import { Button } from '../ui/button';

export interface BearbeitenLeisteProps {
    /** Aktueller Anzeige-Modus: 'lesen' zeigt den Bearbeiten-Knopf, 'bearbeiten' den Fertig-Knopf. */
    modus: 'lesen' | 'bearbeiten';
    /**
     * Nur im Lesen-Modus relevant: darf dieser Nutzer ueberhaupt anfangen zu
     * bearbeiten? false bedeutet, ein anderer haelt das Lock gerade -- der
     * Knopf wird dann deaktiviert, bleibt aber sichtbar (kein Verschwinden
     * der Aktion, siehe Nielsen "Recognition rather than recall").
     */
    kannBearbeiten: boolean;
    /**
     * Sekunden bis zur automatischen Freigabe, direkt aus useIdleTimer
     * (`verbleibendeSekunden`). `null` bedeutet: kein Countdown, kein
     * Banner. Diese Komponente startet und verwaltet keinen eigenen Timer.
     */
    verbleibendeSekunden: number | null;
    /** true nach mehreren fehlgeschlagenen Heartbeat-Versuchen in Folge (siehe useDatensatzLock). */
    verbindungWeg: boolean;
    /** Klick auf den Bearbeiten-Knopf (im Lesen-Modus, nur wenn kannBearbeiten true ist). */
    onBearbeiten: () => void;
    /** Klick auf den Fertig-Knopf (im Bearbeiten-Modus). */
    onFertig: () => void;
}

/**
 * Gemeinsames UI-Element fuer den Datensatz-Sperren-Workflow: der
 * Bearbeiten-/Fertig-Umschalter UND der 60-Sekunden-Countdown der
 * Inaktivitaets-Vorwarnung in einem Baustein (siehe Spec, "Frontend: fuenf
 * neue wiederverwendbare Bausteine").
 *
 * Reine Darstellungskomponente: kein eigener State, kein eigener Timer und
 * kein Netzwerkzugriff. Sekundenzahl, Modus und Berechtigung kommen fertig
 * aus useIdleTimer bzw. useDatensatzLock herein -- genau das verhindert den
 * Fehler, an dem der heutige Dokument-Editor krankt (ein zweiter,
 * eigenstaendiger Timer, der mit dem ersten aus dem Takt geraet).
 */
export function BearbeitenLeiste({
    modus,
    kannBearbeiten,
    verbleibendeSekunden,
    verbindungWeg,
    onBearbeiten,
    onFertig,
}: BearbeitenLeisteProps) {
    return (
        <div className="flex flex-wrap items-center gap-3">
            {modus === 'lesen' ? (
                <Button variant="default" size="sm" disabled={!kannBearbeiten} onClick={onBearbeiten}>
                    <Pencil className="w-4 h-4" aria-hidden="true" />
                    Bearbeiten
                </Button>
            ) : (
                <Button
                    variant="outline"
                    size="sm"
                    className="border-rose-300 text-rose-700 hover:bg-rose-50"
                    onClick={onFertig}
                >
                    <Check className="w-4 h-4" aria-hidden="true" />
                    Fertig
                </Button>
            )}

            {verbleibendeSekunden !== null && (
                <div
                    role="status"
                    aria-live="polite"
                    className="flex items-center gap-1.5 rounded-lg border border-rose-100 bg-rose-50 px-3 py-1.5 text-sm text-slate-700"
                >
                    {`Wird in ${verbleibendeSekunden} Sekunden freigegeben — bewegen Sie die Maus, um weiterzuarbeiten.`}
                </div>
            )}

            {verbindungWeg && (
                <div
                    role="alert"
                    className="flex items-center gap-1.5 rounded-lg border border-rose-200 bg-rose-50 px-3 py-1.5 text-sm text-rose-700"
                >
                    <WifiOff className="w-4 h-4 shrink-0" aria-hidden="true" />
                    Verbindung weg — Ihre Änderungen sind noch nicht gespeichert.
                </div>
            )}
        </div>
    );
}
