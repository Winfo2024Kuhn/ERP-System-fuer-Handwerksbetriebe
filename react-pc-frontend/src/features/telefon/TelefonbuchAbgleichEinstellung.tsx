import { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertTriangle, BookUser, Loader2, RefreshCw, Trash2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';
import { useToast } from '../../components/ui/toast';
import { SettingsCard, TestResultBanner } from '../../components/settings/settingsUi';
import type { TestResult } from '../../components/settings/settingsApi';
import { gleicheTelefonbuchAb, ladeTelefonbuchStatus, schalteTelefonbuch } from './api';
import { formatWann } from './format';
import type { TelefonbuchAbgleichErgebnis, TelefonbuchStatus } from './types';

/** So oft wird der Stand abgefragt, solange ein Lauf läuft. */
export const ABFRAGE_ABSTAND_MS = 2000;

/**
 * Einstellung „Kontakte ins FRITZ!Box-Telefonbuch übertragen“ (nur Administratoren).
 *
 * <p>Der Schalter wird sofort gespeichert. Ist er an (und die Telefon-Anbindung
 * aktiv), hält das ERP in der FRITZ!Box ein eigenes Telefonbuch aktuell – nach
 * jeder Änderung an Kunden, Lieferanten, Mitarbeitern oder Steuerberatern, beim
 * Start und jede Nacht. Ausschalten löscht das Telefonbuch nach Rückfrage aus der
 * FRITZ!Box; ist die Box gerade nicht erreichbar, holt das ERP das nach.</p>
 *
 * <p>Läufe (Abgleich, Löschen) laufen auf dem Server im Hintergrund. Solange einer
 * läuft, fragt die Karte alle {@link ABFRAGE_ABSTAND_MS} ms den Stand ab und zeigt
 * danach das Ergebnis.</p>
 *
 * @param telefonAnbindungAktiv gespeicherter Stand der Telefon-Anbindung aus dem
 *        übergeordneten Bereich – damit der Hinweis nach dem Speichern dort sofort stimmt.
 */
export function TelefonbuchAbgleichEinstellung({ telefonAnbindungAktiv }: { telefonAnbindungAktiv?: boolean }) {
    const toast = useToast();
    const confirm = useConfirm();
    const [status, setStatus] = useState<TelefonbuchStatus | null>(null);
    const [laedt, setLaedt] = useState(true);
    const [ladefehler, setLadefehler] = useState<string | null>(null);
    const [schaltet, setSchaltet] = useState(false);
    const [startet, setStartet] = useState(false);
    /** Nach Klick bzw. Umschalten: Ergebnis zeigen, sobald der Lauf fertig ist. */
    const [wartetAufErgebnis, setWartetAufErgebnis] = useState(false);
    const [abfrageGestoppt, setAbfrageGestoppt] = useState(false);
    const [ergebnis, setErgebnis] = useState<TelefonbuchAbgleichErgebnis | null>(null);

    const lade = useCallback(async () => {
        setLaedt(true);
        setLadefehler(null);
        try {
            setStatus(await ladeTelefonbuchStatus());
        } catch (e) {
            const meldung = e instanceof Error ? e.message : 'Der Stand des FRITZ!Box-Telefonbuchs konnte nicht geladen werden.';
            setLadefehler(meldung);
            toast.error(meldung);
        } finally {
            setLaedt(false);
        }
    }, [toast]);

    useEffect(() => {
        void lade();
    }, [lade]);

    const laeuft = !!status?.laeuftGerade;

    // Solange ein Lauf läuft: Stand abfragen. Beim Verlassen der Seite wird die Abfrage abgebrochen.
    useEffect(() => {
        if (!laeuft || abfrageGestoppt) return undefined;
        let abgebrochen = false;
        const timer = window.setTimeout(async () => {
            try {
                const neu = await ladeTelefonbuchStatus();
                if (!abgebrochen) setStatus(neu);
            } catch (e) {
                if (abgebrochen) return;
                setAbfrageGestoppt(true);
                toast.error(e instanceof Error ? e.message : 'Der Stand des FRITZ!Box-Telefonbuchs konnte nicht abgefragt werden.');
            }
        }, ABFRAGE_ABSTAND_MS);
        return () => {
            abgebrochen = true;
            window.clearTimeout(timer);
        };
    }, [laeuft, abfrageGestoppt, status, toast]);

    // Lauf fertig: Ergebnis zeigen, Fehler zusätzlich als Toast.
    useEffect(() => {
        if (!wartetAufErgebnis || !status || status.laeuftGerade) return;
        setWartetAufErgebnis(false);
        const letztes = status.letztesErgebnis;
        if (!letztes) return;
        setErgebnis(letztes);
        if (!letztes.erfolgreich) toast.error(letztes.meldung || 'Das FRITZ!Box-Telefonbuch konnte nicht abgeglichen werden.');
    }, [wartetAufErgebnis, status, toast]);

    const anbindungAktiv = telefonAnbindungAktiv ?? status?.telefonAnbindungAktiv ?? false;
    const telefonbuchName = status?.telefonbuchName ?? 'ERP-Kontakte';
    const loeschungOffen = !!status && !status.aktiv && status.loeschungOffen;

    const schalten = async (aktiv: boolean) => {
        if (!aktiv) {
            const ok = await confirm({
                title: 'Übertragung ausschalten?',
                message: `Das Telefonbuch „${telefonbuchName}“ wird dabei aus der FRITZ!Box gelöscht. Andere Telefonbücher `
                    + 'bleiben unberührt. Beim Wiedereinschalten wird es neu angelegt.',
                confirmLabel: 'Ausschalten und löschen',
                variant: 'danger',
            });
            if (!ok) return;
        }
        const vorher = status;
        // Sofort umlegen, damit der Haken nicht hinter dem Klick herhinkt; bei Fehler zurück.
        setStatus((alt) => (alt ? { ...alt, aktiv } : alt));
        setSchaltet(true);
        setErgebnis(null);
        try {
            const neu = await schalteTelefonbuch(aktiv);
            setStatus(neu);
            setAbfrageGestoppt(false);
            setWartetAufErgebnis(neu.laeuftGerade);
            toast.success(aktiv
                ? 'Die Kontakte werden jetzt ins FRITZ!Box-Telefonbuch übertragen.'
                : `Ausgeschaltet. Das Telefonbuch „${telefonbuchName}“ wird aus der FRITZ!Box gelöscht.`);
        } catch (e) {
            setStatus(vorher);
            toast.error(e instanceof Error ? e.message : 'Die Einstellung konnte nicht gespeichert werden.');
        } finally {
            setSchaltet(false);
        }
    };

    const starten = async () => {
        setStartet(true);
        setErgebnis(null);
        try {
            setStatus(await gleicheTelefonbuchAb());
            setAbfrageGestoppt(false);
            setWartetAufErgebnis(true);
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Das FRITZ!Box-Telefonbuch konnte nicht abgeglichen werden.');
        } finally {
            setStartet(false);
        }
    };

    const ergebnisAnzeige = useMemo<TestResult | null>(() => {
        if (!ergebnis) return null;
        if (!ergebnis.erfolgreich) return { success: false, message: ergebnis.meldung };
        if (status && !status.aktiv) return { success: true, message: ergebnis.meldung };
        const aenderungen = [
            ergebnis.neu > 0 ? `${ergebnis.neu} neu` : null,
            ergebnis.geaendert > 0 ? `${ergebnis.geaendert} geändert` : null,
            ergebnis.geloescht > 0 ? `${ergebnis.geloescht} gelöscht` : null,
        ].filter(Boolean);
        return {
            success: true,
            message: aenderungen.length === 0
                ? `Das Telefonbuch ist schon aktuell (${ergebnis.eintraege} Einträge).`
                : `Abgeglichen: ${aenderungen.join(', ')}. Jetzt ${ergebnis.eintraege} Einträge.`,
        };
    }, [ergebnis, status]);

    const knopfGesperrt = !status?.aktiv && !loeschungOffen
        ? 'Erst „Kontakte ins FRITZ!Box-Telefonbuch übertragen“ einschalten'
        : status?.aktiv && !anbindungAktiv ? 'Erst die Telefon-Anbindung aktivieren und speichern' : undefined;
    const knopfText = laeuft
        ? (loeschungOffen ? 'Wird gelöscht …' : 'Wird abgeglichen …')
        : (loeschungOffen ? 'Löschen jetzt nachholen' : 'Jetzt abgleichen');

    return (
        <SettingsCard
            icon={<BookUser aria-hidden="true" className="h-5 w-5 text-rose-600" />}
            title="FRITZ!Box-Telefonbuch"
            info={(
                <InfoButton titel="Kontakte ins FRITZ!Box-Telefonbuch übertragen">
                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                        <p>
                            Das ERP legt in der FRITZ!Box ein eigenes Telefonbuch „{telefonbuchName}“ an und hält es aktuell:
                            kurz nach jeder Änderung, beim Start des Servers und jede Nacht. Mit dabei sind Kunden, aktive
                            Lieferanten und Mitarbeiter sowie Steuerberater mit Ansprechpartnern – gesperrte und anonymisierte
                            Kunden nicht.
                        </p>
                    </InfoAbschnitt>
                    <InfoAbschnitt titel="Was bewirkt das?">
                        <p>
                            Bei Anrufen zeigen Telefone und FRITZ!App Fon den Namen. Andere Telefonbücher der FRITZ!Box
                            ändert das ERP nie. Von Hand angelegte Einträge in „{telefonbuchName}“ werden beim nächsten
                            Abgleich gelöscht – eigene Kontakte bitte in ein anderes Telefonbuch.
                        </p>
                        <p>
                            Ausschalten löscht das Telefonbuch „{telefonbuchName}“ aus der FRITZ!Box. Ist sie gerade nicht
                            erreichbar, holt das ERP das nach.
                        </p>
                    </InfoAbschnitt>
                </InfoButton>
            )}
            description={<p>Damit Telefone und Handys bei Anrufen den Namen aus dem ERP zeigen.</p>}
        >
            {laedt ? (
                <div className="space-y-3" aria-label="Lade Telefonbuch-Stand">
                    <div className="h-5 w-2/3 rounded bg-slate-100 motion-safe:animate-pulse" />
                    <div className="h-16 rounded-lg bg-slate-100 motion-safe:animate-pulse" />
                </div>
            ) : ladefehler && !status ? (
                <div role="alert" className="flex flex-col items-start gap-3 rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800 sm:flex-row sm:items-center">
                    <span className="flex-1">{ladefehler}</span>
                    <Button variant="outline" size="sm" onClick={() => void lade()}>Erneut laden</Button>
                </div>
            ) : (
                <div className="space-y-4">
                    <label className="flex cursor-pointer items-start gap-3 select-none">
                        <input
                            type="checkbox"
                            checked={!!status?.aktiv}
                            disabled={schaltet}
                            onChange={(e) => void schalten(e.target.checked)}
                            className="mt-1 h-4 w-4 accent-rose-600 focus:ring-2 focus:ring-rose-500 disabled:cursor-wait"
                        />
                        <span>
                            <span className="flex items-center gap-2 font-medium text-slate-900">
                                Kontakte ins FRITZ!Box-Telefonbuch übertragen
                                {schaltet && <Loader2 aria-hidden="true" className="h-4 w-4 text-slate-400 motion-safe:animate-spin" />}
                            </span>
                            <span className="block text-xs text-slate-500">
                                Aus: Es wird nichts mehr übertragen und das Telefonbuch „{telefonbuchName}“ wird aus der FRITZ!Box gelöscht.
                            </span>
                        </span>
                    </label>

                    {status?.aktiv && !anbindungAktiv && (
                        <p role="note" className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
                            Die Telefon-Anbindung ist ausgeschaltet. Übertragen wird erst, wenn sie oben aktiviert und gespeichert ist.
                        </p>
                    )}

                    {loeschungOffen && !laeuft && (
                        <p role="note" className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
                            <AlertTriangle aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
                            <span>
                                Das Telefonbuch „{telefonbuchName}“ ist noch nicht aus der FRITZ!Box gelöscht. Das ERP versucht es
                                beim nächsten Start, jede Nacht und mit „Löschen jetzt nachholen“ erneut.
                            </span>
                        </p>
                    )}

                    <ul className="space-y-1 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-600">
                        <li>
                            In der FRITZ!App Fon und an den Telefonen das Telefonbuch
                            <strong className="text-slate-900"> „{telefonbuchName}“</strong> auswählen.
                        </li>
                        <li>Die Nummern der Mitarbeiter sind dort für alle sichtbar, die das Telefonbuch nutzen.</li>
                    </ul>

                    <dl className="grid gap-3 text-sm sm:grid-cols-3">
                        <div>
                            <dt className="text-xs text-slate-500">Zuletzt abgeglichen</dt>
                            <dd className="font-medium text-slate-900">
                                {status?.zuletztAbgeglichen ? formatWann(status.zuletztAbgeglichen) : 'noch nie'}
                            </dd>
                        </div>
                        <div>
                            <dt className="text-xs text-slate-500">Einträge im Telefonbuch</dt>
                            <dd className="font-medium tabular-nums text-slate-900">{status?.anzahlEintraege ?? 0}</dd>
                        </div>
                        <div>
                            <dt className="text-xs text-slate-500">Letzter Fehler</dt>
                            <dd className={status?.letzterFehler ? 'break-words font-medium text-rose-700' : 'text-slate-500'}>
                                {status?.letzterFehler || 'keiner'}
                            </dd>
                        </div>
                    </dl>

                    <div className="flex flex-wrap items-center gap-3">
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={() => void starten()}
                            disabled={startet || laeuft || !!knopfGesperrt}
                            title={knopfGesperrt}
                        >
                            {startet || laeuft
                                ? <Loader2 aria-hidden="true" className="h-4 w-4 motion-safe:animate-spin" />
                                : loeschungOffen
                                    ? <Trash2 aria-hidden="true" className="h-4 w-4" />
                                    : <RefreshCw aria-hidden="true" className="h-4 w-4" />}
                            {knopfText}
                        </Button>
                        {knopfGesperrt && <span className="text-xs text-slate-500">{knopfGesperrt}.</span>}
                        {laeuft && !knopfGesperrt && (
                            <span role="status" className="text-xs text-slate-500">Läuft im Hintergrund – Sie können die Seite verlassen.</span>
                        )}
                    </div>
                    <TestResultBanner result={ergebnisAnzeige} />
                </div>
            )}
        </SettingsCard>
    );
}
