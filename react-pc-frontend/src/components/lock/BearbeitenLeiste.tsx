import { Check, Pencil, Timer, WifiOff } from 'lucide-react';
import { useId } from 'react';
import { Button } from '../ui/button';

export interface BearbeitenLeisteProps {
    /** Aktueller Anzeige-Modus: 'lesen' zeigt den Bearbeiten-Knopf, 'bearbeiten' den Fertig-Knopf. */
    modus: 'lesen' | 'bearbeiten';
    /**
     * Nur im Lesen-Modus relevant: ist ein Klick auf "Bearbeiten" gerade
     * sinnvoll? true bei frisch freigegebenem ('idle'), fremd gesperrtem
     * ('locked-by-other') und bereits gehaltenem Lock ('acquired') -- ein
     * Klick loest dann ggf. einen (erneuten) Erwerbsversuch aus. Nur
     * waehrend ein Erwerb laeuft ('loading') oder nach einem Fehler
     * ('error') ist der Wert false, weil ein Klick dort wirkungslos waere
     * (siehe useDatensatzLock, Kontext-Log Nachbesserung 2). Der Knopf
     * bleibt in jedem Fall sichtbar, nur deaktiviert (kein Verschwinden der
     * Aktion, siehe Nielsen "Recognition rather than recall").
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
    /**
     * Grund, warum der Bearbeiten-Knopf gerade deaktiviert ist (z.B.
     * "Sperre wird geprüft…" waehrend eines laufenden Erwerbs oder der
     * Fehlertext nach einem gescheiterten Erwerb). Wird NUR ausgewertet,
     * wenn kannBearbeiten=false ist -- ein aktivierter Knopf braucht keine
     * Erklaerung. Ohne gesetzten (oder nur aus Leerraum bestehenden) Grund
     * traegt der Knopf gar kein title-Attribut (kein leeres title="", siehe
     * FRONTEND_UI.md "Gulf of Execution": deaktivierte Knoepfe erklaeren
     * per Tooltip WARUM sie deaktiviert sind, statt nur grau und stumm zu
     * sein).
     */
    bearbeitenGesperrtGrund?: string;
    /**
     * true, wenn die aufrufende Seite GERADE den Lesen-Modus ohne fremden
     * Halter zeigt -- steuert den ruhigen Hinweis "Sie lesen nur mit.", der
     * sonst die linke Haelfte der Leiste leer liesse (Design-Review-Befund
     * zu Abschnitt 6). Bewusst ein expliziter Prop statt einer Ableitung
     * aus modus/kannBearbeiten: 'idle' (frisch freigegeben) und
     * 'locked-by-other' (Fremdsperre) liefern in useDatensatzLock BEIDE
     * modus='lesen' und kannBearbeiten=true -- nur die aufrufende Seite
     * weiss, ob sie zusaetzlich GesperrtHinweis (den fremden Halter) zeigt.
     * Standard false, damit eine Seite, die den Prop (noch) nicht setzt,
     * den Hinweis nicht faelschlich neben einem sichtbaren GesperrtHinweis
     * doppelt anzeigt.
     */
    zeigeNurLesenHinweis?: boolean;
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
 *
 * Reihenfolge im Markup (Design-Review zu Abschnitt 6, Nachschaerfung):
 * die drei Status-Baender ("Sie lesen nur mit.", Countdown, Verbindung weg)
 * stehen VOR dem Umschalt-Knopf. Nach dem Knopf folgt hoechstens noch der
 * unsichtbare `sr-only`-Beschreibungs-Span fuer `aria-describedby`
 * (`bearbeitenGesperrtGrund`) -- der nimmt keinen Platz ein und zaehlt fuer
 * das Layout darum nicht mit. Der Knopf ist also das letzte Element, das
 * fuer die Breite des Blocks eine Rolle spielt. Der einzige heutige
 * Verwender (LieferantDokumentModal) bindet diese Leiste in ein
 * `justify-between`-Layout ein, das sie als Ganzes rechtsbuendig ausrichtet
 * -- waechst ein Band, verschiebt sich dadurch die LINKE Kante des gesamten
 * Blocks, nicht seine rechte. Ein Knopf, der als erstes Kind an der linken
 * Kante klebt, sprang deshalb bei jedem erscheinenden Band um dessen volle
 * Breite nach links (~540px beim Countdown-Text). Nach den Baendern
 * platziert bleibt der Knopf dagegen an der rechten (fixen) Kante des
 * Blocks stehen, egal wie viele Baender davor erscheinen oder verschwinden.
 */
export function BearbeitenLeiste({
    modus,
    kannBearbeiten,
    verbleibendeSekunden,
    verbindungWeg,
    bearbeitenGesperrtGrund,
    zeigeNurLesenHinweis = false,
    onBearbeiten,
    onFertig,
}: BearbeitenLeisteProps) {
    const gesperrtGrundId = useId();
    // Ein Grund wird nur gezeigt, wenn der Knopf tatsaechlich deaktiviert
    // ist, und nur, wenn er nach dem Trimmen nicht leer ist -- sonst kein
    // title-Attribut ueberhaupt (kein title="").
    const grund = !kannBearbeiten ? bearbeitenGesperrtGrund?.trim() || undefined : undefined;

    return (
        <div className="flex flex-wrap items-center gap-3">
            {modus === 'lesen' && zeigeNurLesenHinweis && (
                <p className="text-sm text-slate-500">Sie lesen nur mit.</p>
            )}

            {verbleibendeSekunden !== null && (
                <div
                    role="status"
                    aria-live="polite"
                    className="flex items-center gap-1.5 rounded-lg border border-amber-300 bg-amber-50 px-3 py-1.5 text-sm font-medium text-amber-800"
                >
                    <Timer className="w-4 h-4 shrink-0 text-amber-600" aria-hidden="true" />
                    {`Wird in ${verbleibendeSekunden} Sekunden freigegeben — bewegen Sie die Maus, um weiterzuarbeiten.`}
                </div>
            )}

            {verbindungWeg && (
                <div
                    role="alert"
                    className="flex items-center gap-1.5 rounded-lg border border-red-300 bg-red-50 px-3 py-1.5 text-sm font-semibold text-red-700"
                >
                    <WifiOff className="w-4 h-4 shrink-0 text-red-600" aria-hidden="true" />
                    Verbindung weg — Ihre Änderungen sind noch nicht gespeichert.
                </div>
            )}

            {modus === 'lesen' ? (
                <>
                    <Button
                        variant="default"
                        size="sm"
                        disabled={!kannBearbeiten}
                        onClick={onBearbeiten}
                        title={grund}
                        aria-describedby={grund ? gesperrtGrundId : undefined}
                    >
                        <Pencil className="w-4 h-4" aria-hidden="true" />
                        Bearbeiten
                    </Button>
                    {grund && (
                        <span id={gesperrtGrundId} className="sr-only">
                            {grund}
                        </span>
                    )}
                </>
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
        </div>
    );
}
