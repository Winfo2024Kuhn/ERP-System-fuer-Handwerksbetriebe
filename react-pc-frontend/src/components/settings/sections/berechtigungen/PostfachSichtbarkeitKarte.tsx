import { useCallback, useEffect, useState } from 'react';
import { AlertCircle, Inbox, MailCheck, RefreshCw } from 'lucide-react';
import { Button } from '../../../ui/button';
import { useToast } from '../../../ui/toast';
import { SaveButton, SettingsCard } from '../../settingsUi';
import { parseErrorMessage } from '../../settingsApi';
import { cn } from '../../../../lib/utils';
import {
    immerFuerAlleSichtbar,
    parsePostfach,
    parsePostfaecher,
    parseSichtbarkeitsAbteilungen,
    parseSichtbarkeitsBenutzer,
    sichtbarkeitAusPostfach,
    sichtbarkeitGeaendert,
    type PostfachDto,
    type PostfachSichtbarkeitRequest,
    type SichtbarkeitsAuswahl,
} from '../../../../features/email/postfach';
import { PostfachSichtbarkeit } from './PostfachSichtbarkeit';
import { useNachladeListe } from './useNachladeListe';

const LADEFEHLER = 'Postfächer konnten nicht geladen werden.';

/** Kopie ohne den Eintrag `id`. */
function ohne<T>(eintraege: Record<number, T>, id: number): Record<number, T> {
    const rest = { ...eintraege };
    delete rest[id];
    return rest;
}

/** Link auf einen anderen Reiter der Einstellungen (wechselt den Reiter über die Adresse). */
const reiterLink = 'font-medium text-rose-700 underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 rounded';

/**
 * Einstellungen → Berechtigungen: Wer sieht welches E-Mail-Postfach?
 *
 * <p>Pro Postfach eine Zeile mit „Alle im Betrieb“ / „Nur bestimmte“ und
 * eigenem Speichern-Knopf (`PUT /api/postfaecher/{id}/sichtbarkeit`). Die
 * Häkchen-Listen für Abteilungen und Benutzer werden nur einmal geladen –
 * und erst, wenn ein Postfach auf „Nur bestimmte“ steht.</p>
 */
export function PostfachSichtbarkeitKarte() {
    const toast = useToast();
    const [postfaecher, setPostfaecher] = useState<PostfachDto[]>([]);
    const [laedt, setLaedt] = useState(true);
    const [ladeFehler, setLadeFehler] = useState<string | null>(null);
    const [entwuerfe, setEntwuerfe] = useState<Record<number, SichtbarkeitsAuswahl>>({});
    const [speichertId, setSpeichertId] = useState<number | null>(null);
    const [zeilenFehler, setZeilenFehler] = useState<Record<number, string>>({});

    const laden = useCallback(async () => {
        try {
            const res = await fetch('/api/postfaecher');
            if (!res.ok) throw new Error(await parseErrorMessage(res, LADEFEHLER));
            setPostfaecher(parsePostfaecher(await res.json()));
            setEntwuerfe({});
            setZeilenFehler({});
            setLadeFehler(null);
        } catch (err) {
            const text = err instanceof Error && err.message ? err.message : LADEFEHLER;
            setLadeFehler(text);
            toast.error(text);
        } finally {
            setLaedt(false);
        }
    }, [toast]);

    useEffect(() => { void laden(); }, [laden]);

    const auswahl = (postfach: PostfachDto) => entwuerfe[postfach.id] ?? sichtbarkeitAusPostfach(postfach);
    const brauchtListen = postfaecher.some(p => !immerFuerAlleSichtbar(p) && !auswahl(p).sichtbarFuerAlle);

    const abteilungen = useNachladeListe('/api/abteilungen/berechtigungen', brauchtListen,
        parseSichtbarkeitsAbteilungen, 'Abteilungen konnten nicht geladen werden.');
    const benutzer = useNachladeListe('/api/frontend-users', brauchtListen,
        parseSichtbarkeitsBenutzer, 'Benutzer konnten nicht geladen werden.');

    const aendere = (postfach: PostfachDto, wert: SichtbarkeitsAuswahl) => {
        setEntwuerfe(vorher => ({ ...vorher, [postfach.id]: wert }));
        setZeilenFehler(vorher => ohne(vorher, postfach.id));
    };

    const speichern = async (postfach: PostfachDto) => {
        const body: PostfachSichtbarkeitRequest = auswahl(postfach);
        setSpeichertId(postfach.id);
        try {
            const res = await fetch(`/api/postfaecher/${postfach.id}/sichtbarkeit`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body),
            });
            if (!res.ok) {
                const text = await parseErrorMessage(res, 'Sichtbarkeit konnte nicht gespeichert werden.');
                setZeilenFehler(vorher => ({ ...vorher, [postfach.id]: text }));
                toast.error(text);
                return;
            }
            const gespeichert = parsePostfach(await res.json().catch(() => null));
            toast.success(`Sichtbarkeit für ${postfach.emailAdresse} gespeichert.`);
            if (!gespeichert) {
                // Gespeichert, aber die Antwort ist unbrauchbar: frischen Stand vom Server holen.
                await laden();
                return;
            }
            setPostfaecher(vorher => vorher.map(p => (p.id === postfach.id ? { ...p, ...gespeichert } : p)));
            setEntwuerfe(vorher => ohne(vorher, postfach.id));
        } catch {
            const text = 'Verbindung zum Server fehlgeschlagen. Sichtbarkeit wurde nicht gespeichert.';
            setZeilenFehler(vorher => ({ ...vorher, [postfach.id]: text }));
            toast.error(text);
        } finally {
            setSpeichertId(null);
        }
    };

    return (
        <SettingsCard
            icon={<MailCheck className="h-5 w-5 text-rose-600" aria-hidden="true" />}
            title="E-Mail-Postfächer – wer sieht welches Postfach?"
            description={
                <p>
                    Hier legst du fest, wer im E-Mail-Center die Mails eines Postfachs sieht. Das Hauptpostfach
                    sieht jeder. Der Inhaber eines Postfachs und Admins sehen es immer. Mails, die einem Projekt,
                    einer Anfrage oder einem Lieferanten zugeordnet sind, sieht jeder in diesen Reitern.
                </p>
            }
        >
            {laedt ? (
                <div className="space-y-3" aria-busy="true" aria-label="Postfächer werden geladen">
                    {[0, 1].map(i => <div key={i} className="h-20 rounded-xl bg-slate-100 motion-safe:animate-pulse" />)}
                </div>
            ) : ladeFehler ? (
                <div role="alert" className="flex flex-col items-start gap-3 rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800 sm:flex-row sm:items-center sm:justify-between">
                    <span className="flex items-center gap-2"><AlertCircle className="h-4 w-4" aria-hidden="true" />{ladeFehler}</span>
                    <Button variant="outline" size="sm" onClick={() => { setLaedt(true); void laden(); }}
                        className="border-rose-300 text-rose-700 hover:bg-rose-100">
                        <RefreshCw className="h-4 w-4" />
                        Erneut laden
                    </Button>
                </div>
            ) : postfaecher.length === 0 ? (
                <div className="flex flex-col items-center gap-2 rounded-xl border border-dashed border-slate-300 px-6 py-8 text-center">
                    <Inbox className="h-8 w-8 text-slate-400" aria-hidden="true" />
                    <p className="font-medium text-slate-700">Noch kein Postfach eingerichtet.</p>
                    <p className="max-w-md text-sm text-slate-500">
                        Postfächer legst du unter{' '}
                        <a href="#email" className={reiterLink}>Einstellungen → E-Mail</a>{' '}
                        an. Danach stellst du hier ein, wer sie sehen darf.
                    </p>
                </div>
            ) : (
                <ul className="space-y-3" aria-label="Sichtbarkeit der Postfächer">
                    {postfaecher.map(postfach => {
                        const wert = auswahl(postfach);
                        const geaendert = sichtbarkeitGeaendert(postfach, wert);
                        const speichert = speichertId === postfach.id;
                        const fehler = zeilenFehler[postfach.id];
                        return (
                            <li key={postfach.id} className={cn('rounded-xl border border-slate-200 p-4',
                                !postfach.aktiv && 'bg-slate-50/70')}>
                                <div className="mb-3 flex flex-wrap items-center gap-2">
                                    <span className="break-all font-semibold text-slate-900">{postfach.emailAdresse}</span>
                                    {postfach.anzeigename && <span className="text-sm text-slate-500">{postfach.anzeigename}</span>}
                                    {postfach.hauptpostfach && (
                                        <span className="rounded-full border border-rose-200 bg-rose-50 px-2 py-0.5 text-xs font-medium text-rose-700">
                                            Hauptpostfach
                                        </span>
                                    )}
                                    {postfach.fuerGeschaeftsdokumente && (
                                        <span className="rounded-full border border-slate-200 bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700">
                                            Rechnungen &amp; Mahnungen
                                        </span>
                                    )}
                                    {postfach.laeuftAus && (
                                        <span className="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-xs font-medium text-slate-600"
                                            title="Läuft aus: Mails kommen weiter an, Antworten gehen über das Hauptpostfach raus, für neue Mails nicht mehr wählbar.">
                                            läuft aus
                                        </span>
                                    )}
                                    {!postfach.aktiv && (
                                        <span className="rounded-full border border-slate-200 bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600"
                                            title={immerFuerAlleSichtbar(postfach)
                                                ? 'Solange es ausgeschaltet ist, sehen nur Admins seine Mails.'
                                                : 'Solange es ausgeschaltet ist, sehen nur Admins seine Mails. Die Auswahl gilt wieder, sobald es eingeschaltet ist.'}>
                                            ausgeschaltet
                                        </span>
                                    )}
                                </div>

                                {postfach.hauptpostfach ? (
                                    <p className="text-sm text-slate-600">Das Hauptpostfach sieht jeder im Betrieb.</p>
                                ) : postfach.fuerGeschaeftsdokumente ? (
                                    <p className="text-sm text-slate-600">Das Postfach für Rechnungen &amp; Mahnungen sieht jeder im Betrieb.</p>
                                ) : (
                                    <>
                                        <PostfachSichtbarkeit
                                            postfachName={postfach.emailAdresse}
                                            wert={wert}
                                            onChange={(neu) => aendere(postfach, neu)}
                                            abteilungen={abteilungen}
                                            benutzer={benutzer}
                                            bekannteAbteilungen={postfach.sichtbarFuerAbteilungen}
                                            bekannteBenutzer={postfach.sichtbarFuerBenutzer}
                                            disabled={speichert}
                                        />
                                        {fehler && (
                                            <div role="alert" className="mt-3 flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">
                                                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                                                {fehler}
                                            </div>
                                        )}
                                        <SaveButton
                                            onClick={() => void speichern(postfach)}
                                            saving={speichert}
                                            disabled={!geaendert || speichertId !== null}
                                            ariaLabel={`Sichtbarkeit für ${postfach.emailAdresse} speichern`}
                                            title={!geaendert ? 'Noch nichts geändert – erst Auswahl oder Häkchen ändern.' : undefined}
                                        >
                                            {speichert ? 'Wird gespeichert …' : 'Speichern'}
                                        </SaveButton>
                                    </>
                                )}
                            </li>
                        );
                    })}
                </ul>
            )}
        </SettingsCard>
    );
}
