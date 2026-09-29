import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { PhoneOff, RefreshCw } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { ladeAnrufe, ladeSprachnachrichten, ladeStatus } from './api';
import { ArtSymbol } from './ArtSymbol';
import { anrufbeantworterName, formatDauerMinuten, formatWann } from './format';
import { SprachnachrichtEintrag } from './SprachnachrichtEintrag';
import type { Anrufbeantworter, KontaktTyp, Sprachnachricht, TelefonAnruf, ZuordnenZiel } from './types';
import { useZuordnen } from './useZuordnen';
import { ZuordnungsMenue } from './WerAnzeige';

/**
 * Reiter „Anrufe" in der Kunden- bzw. Lieferantenakte: alle Anrufe und
 * Anrufbeantworter-Nachrichten dieses Kontakts, neueste zuerst. Nachrichten
 * lassen sich direkt hier abspielen.
 *
 * <p>Gehört ein Anruf zu einer Nachricht, steht die Nachricht direkt unter
 * dem Anruf statt doppelt in der Liste.</p>
 */

const MAX_ANRUFE = 200;

type Zeile =
    | { art: 'anruf'; zeitpunkt: string; anruf: TelefonAnruf; nachricht: Sprachnachricht | null }
    | { art: 'nachricht'; zeitpunkt: string; nachricht: Sprachnachricht };

interface KontaktAnrufeTabProps {
    typ: KontaktTyp;
    kontaktId: number;
}

export function KontaktAnrufeTab({ typ, kontaktId }: KontaktAnrufeTabProps) {
    const [anrufe, setAnrufe] = useState<TelefonAnruf[]>([]);
    const [nachrichten, setNachrichten] = useState<Sprachnachricht[]>([]);
    const [anrufbeantworter, setAnrufbeantworter] = useState<Anrufbeantworter[]>([]);
    const [laedt, setLaedt] = useState(true);
    const [fehler, setFehler] = useState<string | null>(null);
    const anfrageRef = useRef(0);

    const lade = useCallback(async () => {
        const nummer = ++anfrageRef.current;
        setLaedt(true);
        setFehler(null);
        const filter = typ === 'KUNDE' ? { kundeId: kontaktId } : { lieferantId: kontaktId };
        try {
            const [seite, liste, status] = await Promise.all([
                ladeAnrufe({ ...filter, groesse: MAX_ANRUFE }),
                ladeSprachnachrichten(filter),
                // Nur für die Namen der Anrufbeantworter – ohne geht es auch.
                ladeStatus().catch(() => null),
            ]);
            if (nummer !== anfrageRef.current) return;
            setAnrufe(seite.inhalt);
            setNachrichten(liste);
            setAnrufbeantworter(status?.anrufbeantworter ?? []);
        } catch (e) {
            if (nummer === anfrageRef.current) setFehler(e instanceof Error ? e.message : 'Die Anrufe konnten nicht geladen werden.');
        } finally {
            if (nummer === anfrageRef.current) setLaedt(false);
        }
    }, [typ, kontaktId]);

    useEffect(() => {
        void lade();
    }, [lade]);

    const zeilen = useMemo<Zeile[]>(() => {
        const nachrichtNachId = new Map(nachrichten.map((n) => [n.id, n]));
        const verbraucht = new Set<number>();
        const liste: Zeile[] = anrufe.map((anruf) => {
            const nachricht = anruf.sprachnachrichtId ? nachrichtNachId.get(anruf.sprachnachrichtId) ?? null : null;
            if (nachricht) verbraucht.add(nachricht.id);
            return { art: 'anruf', zeitpunkt: anruf.zeitpunkt, anruf, nachricht };
        });
        nachrichten.filter((n) => !verbraucht.has(n.id))
            .forEach((nachricht) => liste.push({ art: 'nachricht', zeitpunkt: nachricht.zeitpunkt, nachricht }));
        // ISO-Zeitpunkte lassen sich als Text sortieren.
        return liste.sort((a, b) => b.zeitpunkt.localeCompare(a.zeitpunkt));
    }, [anrufe, nachrichten]);

    const ersetzeNachricht = useCallback((neu: Sprachnachricht) => {
        setNachrichten((alt) => alt.map((n) => (n.id === neu.id ? neu : n)));
    }, []);

    // Wer hier die Zuordnung aufhebt oder ändert, nimmt den Eintrag aus dieser Akte – also neu laden.
    const zuordnenAnruf = useZuordnen<TelefonAnruf>(useCallback(() => { void lade(); }, [lade]));
    const zuordnenNachricht = useZuordnen<Sprachnachricht>(useCallback(() => { void lade(); }, [lade]));

    if (fehler) {
        return (
            <div role="alert" className="flex flex-col items-center gap-3 rounded-lg border border-slate-200 bg-white p-10 text-center">
                <p className="text-rose-800">{fehler}</p>
                <Button variant="outline" size="sm" onClick={() => void lade()}>
                    <RefreshCw aria-hidden="true" className="h-4 w-4" />
                    Erneut laden
                </Button>
            </div>
        );
    }
    if (laedt && zeilen.length === 0) {
        return (
            <div role="status" aria-label="Anrufe werden geladen" className="space-y-3">
                {Array.from({ length: 3 }, (_, i) => (
                    <div key={i} className="h-16 rounded-lg bg-slate-100 motion-safe:animate-pulse" />
                ))}
            </div>
        );
    }
    if (zeilen.length === 0) {
        return (
            <div className="flex flex-col items-center justify-center gap-2 py-12 text-center text-slate-400">
                <PhoneOff aria-hidden="true" className="h-12 w-12" />
                <p>Noch keine Anrufe von diesem Kontakt.</p>
            </div>
        );
    }

    return (
        <>
            <ul className="space-y-3" aria-label="Anrufe und Nachrichten" aria-busy={laedt}>
                {zeilen.map((zeile) => {
                    if (zeile.art === 'nachricht') {
                        return (
                            <SprachnachrichtEintrag
                                key={`n-${zeile.nachricht.id}`}
                                nachricht={zeile.nachricht}
                                anrufbeantworterName={anrufbeantworterName(anrufbeantworter, zeile.nachricht.anrufbeantworter)}
                                onGeaendert={ersetzeNachricht}
                                zuordnen={zuordnenNachricht}
                                kompakt
                            />
                        );
                    }
                    const { anruf, nachricht } = zeile;
                    const ziel: ZuordnenZiel = { art: 'anruf', id: anruf.id };
                    return (
                        <li key={`a-${anruf.id}`} className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
                            <div className="flex flex-wrap items-center gap-x-6 gap-y-2">
                                <div className="min-w-[10rem] flex-1">
                                    <ArtSymbol
                                        art={anruf.art}
                                        zusatz={anruf.art === 'ANRUFBEANTWORTER' ? anrufbeantworterName(anrufbeantworter, anruf.anrufbeantworter) : undefined}
                                    />
                                </div>
                                <span className="text-sm tabular-nums text-slate-600">{anruf.nummer || 'Nummer unterdrückt'}</span>
                                <span className="text-sm text-slate-700">{formatWann(anruf.zeitpunkt)}</span>
                                <span className="w-24 text-sm tabular-nums text-slate-500">{formatDauerMinuten(anruf.dauerMinuten)}</span>
                                <ZuordnungsMenue
                                    beschriftung={`Anruf ${formatWann(anruf.zeitpunkt)}`}
                                    zugeordnet
                                    beschaeftigt={zuordnenAnruf.istBeschaeftigt(ziel)}
                                    onAndererKontakt={() => zuordnenAnruf.oeffneDialog(ziel, anruf.nummer)}
                                    onAufheben={() => zuordnenAnruf.aufheben(ziel)}
                                />
                            </div>
                            {nachricht && (
                                <ul className="mt-3">
                                    <SprachnachrichtEintrag
                                        nachricht={nachricht}
                                        anrufbeantworterName={anrufbeantworterName(anrufbeantworter, nachricht.anrufbeantworter)}
                                        onGeaendert={ersetzeNachricht}
                                        zuordnen={zuordnenNachricht}
                                        kompakt
                                        ohneZuordnung
                                    />
                                </ul>
                            )}
                        </li>
                    );
                })}
            </ul>
            {zuordnenAnruf.dialogElement}
            {zuordnenNachricht.dialogElement}
        </>
    );
}
