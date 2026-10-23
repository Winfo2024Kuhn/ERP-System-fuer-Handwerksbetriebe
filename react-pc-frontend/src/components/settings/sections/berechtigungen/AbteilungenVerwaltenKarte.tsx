import { useCallback, useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { AlertCircle, Building2, Check, Loader2, Pencil, Plus, RefreshCw, Trash2, X } from 'lucide-react';
import { Button } from '../../../ui/button';
import { Input } from '../../../ui/input';
import { InfoAbschnitt, InfoButton } from '../../../ui/info-button';
import { useConfirm } from '../../../ui/confirm-dialog';
import { useToast } from '../../../ui/toast';
import { SettingsCard } from '../../settingsUi';

interface Abteilung {
    id: number;
    name: string;
}

interface AbteilungenVerwaltenKarteProps {
    /** Nach jedem Anlegen, Umbenennen oder Löschen – damit die Rechte-Karten darunter nachziehen. */
    onGeaendert: () => void;
}

const LADEFEHLER = 'Abteilungen konnten nicht geladen werden.';
const NAME_FEHLT = 'Bitte einen Namen für die Abteilung eingeben.';
const VERBINDUNG = 'Verbindung zum Server fehlgeschlagen.';

/** So lang darf ein Name höchstens sein – das Programm speichert nicht mehr. */
const NAME_MAX = 100;

const ICON_KNOPF = 'h-8 w-8 p-0 text-slate-500 hover:bg-rose-50 hover:text-rose-700';

/**
 * Meldung des Servers aus `{ "message": "…" }` – sonst der eigene Text. Bewusst nicht der rohe
 * Antworttext: Eine Spring-Fehlerseite oder ein JSON ohne `message` soll nie im Toast landen.
 */
async function serverMeldung(res: Response, fallback: string): Promise<string> {
    try {
        const daten: unknown = await res.json();
        const message = (daten as { message?: unknown } | null)?.message;
        if (typeof message === 'string' && message.trim()) return message.trim();
    } catch {
        // Kein JSON im Body – dann bleibt es beim eigenen Text.
    }
    return fallback;
}

function parseAbteilungen(daten: unknown): Abteilung[] {
    if (!Array.isArray(daten)) return [];
    return daten
        .filter((a): a is Abteilung => typeof a?.id === 'number' && typeof a?.name === 'string')
        .map(({ id, name }) => ({ id, name }))
        .sort((a, b) => a.name.localeCompare(b.name, 'de'));
}

function AbteilungenInfo() {
    return (
        <InfoButton titel="Abteilungen">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>
                    Eine Abteilung fasst Mitarbeiter zusammen, z. B. Werkstatt, Montage oder Büro. Jede Abteilung hat
                    darunter ihre eigenen Rechte. Beim Mitarbeiter wählen Sie aus, zu welcher Abteilung er gehört, und
                    jeder Arbeitsgang gehört ebenfalls zu einer Abteilung.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>
                    Umbenennen ändert nur den Namen – Mitarbeiter, Arbeitsgänge und Rechte bleiben zugeordnet. Löschen
                    geht erst, wenn kein Mitarbeiter und kein Arbeitsgang mehr dazugehört. Dabei verschwinden auch die
                    Rechte der Abteilung und ihre Freigaben für E-Mail-Postfächer. Bereits gebuchte Zeiten bleiben
                    unverändert.
                </p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

/**
 * Einstellungen → Abteilungen & Rechte: Abteilungen anlegen, umbenennen und löschen.
 *
 * <p>Lädt `GET /api/abteilungen` selbst. Nach jeder Änderung meldet sie sich über
 * `onGeaendert`, damit unter „Rechte pro Abteilung“ sofort die passende Karte
 * erscheint, umbenannt wird oder verschwindet. Fehlermeldungen des Servers
 * (z. B. „gibt es schon“, „noch zugeordnet“) werden wörtlich angezeigt.</p>
 */
export function AbteilungenVerwaltenKarte({ onGeaendert }: AbteilungenVerwaltenKarteProps) {
    const toast = useToast();
    const confirm = useConfirm();
    const [abteilungen, setAbteilungen] = useState<Abteilung[]>([]);
    const [laedt, setLaedt] = useState(true);
    const [ladeFehler, setLadeFehler] = useState<string | null>(null);
    const [neuerName, setNeuerName] = useState('');
    const [bearbeitung, setBearbeitung] = useState<{ id: number; name: string } | null>(null);
    /** Welche Aktion gerade läuft – sperrt alle Knöpfe gegen Doppelklicks. */
    const [laeuft, setLaeuft] = useState<'anlegen' | `umbenennen-${number}` | `loeschen-${number}` | null>(null);
    const zeilenRefs = useRef<Record<number, HTMLLIElement | null>>({});
    const fokusZurueckAn = useRef<number | null>(null);

    const laden = useCallback(async () => {
        try {
            const res = await fetch('/api/abteilungen');
            if (!res.ok) throw new Error(await serverMeldung(res, LADEFEHLER));
            setAbteilungen(parseAbteilungen(await res.json()));
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

    // Nach Abbrechen/Speichern zurück auf den Stift der Zeile – sonst landet der Tastaturfokus im Nichts.
    useEffect(() => {
        if (bearbeitung !== null || fokusZurueckAn.current === null) return;
        // Außerhalb der Bearbeitung ist der erste Knopf der Zeile der Stift.
        zeilenRefs.current[fokusZurueckAn.current]?.querySelector('button')?.focus();
        fokusZurueckAn.current = null;
    }, [bearbeitung, abteilungen]);

    const nachAenderung = async () => {
        await laden();
        onGeaendert();
    };

    /** Meldet einen Fehler des Servers: 409 als Warnung (lässt sich beheben), alles andere als Fehler. */
    const meldeFehler = async (res: Response, fallback: string) => {
        const text = await serverMeldung(res, fallback);
        if (res.status === 409) toast.warning(text);
        else toast.error(text);
    };

    const anlegen = async () => {
        const name = neuerName.trim();
        if (laeuft) return;
        if (!name) {
            toast.warning(NAME_FEHLT);
            return;
        }
        setLaeuft('anlegen');
        try {
            const res = await fetch('/api/abteilungen', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name }),
            });
            if (!res.ok) {
                await meldeFehler(res, 'Abteilung konnte nicht angelegt werden.');
                return;
            }
            setNeuerName('');
            toast.success(`Abteilung „${name}“ angelegt.`);
            await nachAenderung();
        } catch {
            toast.error(`${VERBINDUNG} Abteilung wurde nicht angelegt.`);
        } finally {
            setLaeuft(null);
        }
    };

    const bearbeitungBeenden = (id: number) => {
        fokusZurueckAn.current = id;
        setBearbeitung(null);
    };

    const umbenennen = async (abteilung: Abteilung) => {
        if (!bearbeitung || laeuft) return;
        const name = bearbeitung.name.trim();
        if (!name) {
            toast.warning(NAME_FEHLT);
            return;
        }
        if (name === abteilung.name) {
            bearbeitungBeenden(abteilung.id);
            return;
        }
        setLaeuft(`umbenennen-${abteilung.id}`);
        try {
            const res = await fetch(`/api/abteilungen/${abteilung.id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name }),
            });
            if (!res.ok) {
                await meldeFehler(res, 'Abteilung konnte nicht umbenannt werden.');
                return;
            }
            toast.success(`„${abteilung.name}“ heißt jetzt „${name}“.`);
            bearbeitungBeenden(abteilung.id);
            await nachAenderung();
        } catch {
            toast.error(`${VERBINDUNG} Abteilung wurde nicht umbenannt.`);
        } finally {
            setLaeuft(null);
        }
    };

    const loeschen = async (abteilung: Abteilung) => {
        if (laeuft) return;
        const ok = await confirm({
            title: 'Abteilung löschen?',
            message: `„${abteilung.name}“ wird gelöscht – mit ihren Rechten und Freigaben für E-Mail-Postfächer. Das geht nur, wenn kein Mitarbeiter und kein Arbeitsgang mehr dazugehört.`,
            confirmLabel: 'Löschen',
            cancelLabel: 'Abbrechen',
            variant: 'danger',
        });
        if (!ok) return;
        setLaeuft(`loeschen-${abteilung.id}`);
        try {
            const res = await fetch(`/api/abteilungen/${abteilung.id}`, { method: 'DELETE' });
            if (!res.ok) {
                await meldeFehler(res, 'Abteilung konnte nicht gelöscht werden.');
                return;
            }
            toast.success(`Abteilung „${abteilung.name}“ gelöscht.`);
            await nachAenderung();
        } catch {
            toast.error(`${VERBINDUNG} Abteilung wurde nicht gelöscht.`);
        } finally {
            setLaeuft(null);
        }
    };

    const beiTasteImUmbenennen = (event: KeyboardEvent<HTMLInputElement>, abteilung: Abteilung) => {
        if (event.key === 'Enter') {
            event.preventDefault();
            void umbenennen(abteilung);
        } else if (event.key === 'Escape') {
            event.preventDefault();
            bearbeitungBeenden(abteilung.id);
        }
    };

    const nameLeer = !neuerName.trim();

    return (
        <SettingsCard
            icon={<Building2 className="h-5 w-5 text-rose-600" aria-hidden="true" />}
            title="Abteilungen"
            info={<AbteilungenInfo />}
            description={
                <p>
                    Legen Sie hier Ihre Abteilungen an, z. B. Werkstatt, Montage oder Büro. Für jede Abteilung stellen
                    Sie weiter unten ein, was ihre Mitarbeiter sehen und tun dürfen.
                </p>
            }
        >
            {laedt ? (
                <div className="space-y-2" aria-busy="true" aria-label="Abteilungen werden geladen">
                    {[0, 1, 2].map(i => <div key={i} className="h-11 rounded-lg bg-slate-100 motion-safe:animate-pulse" />)}
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
            ) : abteilungen.length === 0 ? (
                <div className="flex flex-col items-center gap-2 rounded-xl border border-dashed border-slate-300 px-6 py-6 text-center">
                    <Building2 className="h-8 w-8 text-slate-400" aria-hidden="true" />
                    <p className="font-medium text-slate-700">Noch keine Abteilung angelegt.</p>
                    <p className="max-w-md text-sm text-slate-500">Legen Sie unten die erste an.</p>
                </div>
            ) : (
                <ul className="space-y-2" aria-label="Abteilungen">
                    {abteilungen.map(abteilung => {
                        const imUmbenennen = bearbeitung?.id === abteilung.id;
                        const speichert = laeuft === `umbenennen-${abteilung.id}`;
                        const loescht = laeuft === `loeschen-${abteilung.id}`;
                        return (
                            <li key={abteilung.id} ref={(el) => { zeilenRefs.current[abteilung.id] = el; }} className="flex min-h-11 items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-1.5">
                                <Building2 className="h-4 w-4 shrink-0 text-rose-600" aria-hidden="true" />
                                {imUmbenennen ? (
                                    <>
                                        <Input
                                            autoFocus
                                            value={bearbeitung.name}
                                            onChange={(e) => setBearbeitung({ id: abteilung.id, name: e.target.value })}
                                            onKeyDown={(e) => beiTasteImUmbenennen(e, abteilung)}
                                            aria-label={`Neuer Name für „${abteilung.name}“`}
                                            maxLength={NAME_MAX}
                                            disabled={speichert}
                                            className="h-8 min-w-0 flex-1"
                                        />
                                        <Button size="sm" onClick={() => void umbenennen(abteilung)}
                                            disabled={speichert || !bearbeitung.name.trim()}
                                            title={!bearbeitung.name.trim() ? NAME_FEHLT : undefined}>
                                            {speichert ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
                                            Speichern
                                        </Button>
                                        <Button variant="outline" size="sm" onClick={() => bearbeitungBeenden(abteilung.id)} disabled={speichert}>
                                            <X className="h-4 w-4" />
                                            Abbrechen
                                        </Button>
                                    </>
                                ) : (
                                    <>
                                        <span className="min-w-0 flex-1 break-words text-sm font-medium text-slate-900">{abteilung.name}</span>
                                        <Button
                                            variant="ghost" size="sm" className={ICON_KNOPF}
                                            onClick={() => setBearbeitung({ id: abteilung.id, name: abteilung.name })}
                                            disabled={laeuft !== null}
                                            aria-label={`„${abteilung.name}“ umbenennen`} title="Umbenennen">
                                            <Pencil className="h-4 w-4" />
                                        </Button>
                                        <Button
                                            variant="ghost" size="sm"
                                            className="h-8 w-8 p-0 text-slate-500 hover:bg-red-50 hover:text-red-600"
                                            onClick={() => void loeschen(abteilung)}
                                            disabled={laeuft !== null}
                                            aria-label={`„${abteilung.name}“ löschen`} title="Löschen">
                                            {loescht ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
                                        </Button>
                                    </>
                                )}
                            </li>
                        );
                    })}
                </ul>
            )}

            {!laedt && !ladeFehler && (
                <div className="mt-4 flex flex-col gap-2 border-t border-slate-100 pt-4 sm:flex-row">
                    <Input
                        value={neuerName}
                        onChange={(e) => setNeuerName(e.target.value)}
                        onKeyDown={(e) => {
                            if (e.key === 'Enter') {
                                e.preventDefault();
                                void anlegen();
                            }
                        }}
                        placeholder="Neue Abteilung …"
                        aria-label="Name der neuen Abteilung"
                        maxLength={NAME_MAX}
                        disabled={laeuft === 'anlegen'}
                        className="min-w-0 flex-1"
                    />
                    <Button size="sm" onClick={() => void anlegen()}
                        disabled={nameLeer || laeuft !== null}
                        title={nameLeer ? 'Erst einen Namen eingeben.' : undefined}
                        className="sm:self-center">
                        {laeuft === 'anlegen' ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
                        Abteilung anlegen
                    </Button>
                </div>
            )}
        </SettingsCard>
    );
}
