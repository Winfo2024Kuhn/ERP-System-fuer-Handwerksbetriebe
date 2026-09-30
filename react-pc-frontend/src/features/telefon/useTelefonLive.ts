import { useCallback, useEffect, useRef, useState } from 'react';
import { refreshNotifications } from '../../lib/notificationRefresh';
import type { LiveAnruf } from './types';

/**
 * Live-Anrufe für das Anruf-Fenster.
 *
 * <p>Öffnet eine Server-Sent-Events-Verbindung zu `/api/telefon/live` und
 * hört auf das Ereignis `anruf`. Jede Verbindung (FRITZ!Box-Verbindungs-ID)
 * wird ein Eintrag; der neueste steht vorn.</p>
 *
 * <ul>
 *   <li>Bricht die Verbindung endgültig ab (z. B. Server neu gestartet),
 *       verbindet sich der Hook mit wachsender Pause selbst neu.</li>
 *   <li>Beim Aushängen (Abmelden, Seite verlassen) wird die Verbindung
 *       sauber geschlossen.</li>
 *   <li>„Schließen" blendet einen Anruf nur für diesen Benutzer aus.</li>
 *   <li>„Festhalten" lässt einen verpassten Anruf stehen, bis er geschlossen
 *       wird (z. B. solange man zurückruft).</li>
 *   <li>`BEENDET`: angenommene Anrufe verschwinden sofort, nicht
 *       angenommene zeigen drei Sekunden „Verpasst". Danach wird die Glocke
 *       aufgefrischt.</li>
 * </ul>
 */

export interface LiveAnrufAnzeige extends LiveAnruf {
    /** Ab wann das Gespräch läuft (ms seit 1970) – für die laufende Dauer. */
    gespraechSeit: number | null;
    /** Vorbei und nicht angenommen: wird noch kurz als „Verpasst" gezeigt. */
    verpasst: boolean;
}

interface Eintrag extends LiveAnrufAnzeige {
    eingegangen: number;
    geschlossen: boolean;
}

export const VERPASST_ANZEIGEDAUER_MS = 3000;
const ERSTE_WARTEZEIT_MS = 3000;
const LAENGSTE_WARTEZEIT_MS = 60_000;

type EventSourceFabrik = (url: string) => EventSource;

const standardFabrik: EventSourceFabrik = (url) => new EventSource(url);

function liesAnruf(daten: string): LiveAnruf | null {
    try {
        const roh = JSON.parse(daten);
        if (!roh || typeof roh.verbindungsId !== 'string' || typeof roh.status !== 'string') return null;
        return {
            verbindungsId: roh.verbindungsId,
            status: roh.status,
            nummer: typeof roh.nummer === 'string' ? roh.nummer : '',
            kontakt: roh.kontakt ?? null,
            kandidaten: Array.isArray(roh.kandidaten) ? roh.kandidaten : [],
            angenommen: roh.angenommen === true,
        };
    } catch {
        return null;
    }
}

export function useTelefonLive(aktiv: boolean, fabrik: EventSourceFabrik = standardFabrik) {
    const [eintraege, setEintraege] = useState<Record<string, Eintrag>>({});
    const timerRef = useRef(new Map<string, ReturnType<typeof setTimeout>>());

    const entferne = useCallback((verbindungsId: string) => {
        const timer = timerRef.current.get(verbindungsId);
        if (timer) clearTimeout(timer);
        timerRef.current.delete(verbindungsId);
        setEintraege((alt) => {
            if (!(verbindungsId in alt)) return alt;
            const neu = { ...alt };
            delete neu[verbindungsId];
            return neu;
        });
    }, []);

    const verarbeite = useCallback((anruf: LiveAnruf) => {
        if (anruf.status === 'BEENDET') {
            refreshNotifications();
            setEintraege((alt) => {
                const bisher = alt[anruf.verbindungsId];
                // Angenommen, schon geschlossen oder nie gesehen: einfach weg.
                if (!bisher || bisher.geschlossen || anruf.angenommen || bisher.angenommen) {
                    if (!bisher) return alt;
                    const neu = { ...alt };
                    delete neu[anruf.verbindungsId];
                    return neu;
                }
                return { ...alt, [anruf.verbindungsId]: { ...bisher, ...anruf, verpasst: true } };
            });
            const alterTimer = timerRef.current.get(anruf.verbindungsId);
            if (alterTimer) clearTimeout(alterTimer);
            timerRef.current.set(anruf.verbindungsId,
                setTimeout(() => entferne(anruf.verbindungsId), VERPASST_ANZEIGEDAUER_MS));
            return;
        }
        setEintraege((alt) => {
            const bisher = alt[anruf.verbindungsId];
            const gespraechSeit = anruf.status === 'IM_GESPRAECH'
                ? (bisher?.gespraechSeit ?? Date.now())
                : (bisher?.gespraechSeit ?? null);
            return {
                ...alt,
                [anruf.verbindungsId]: {
                    ...anruf,
                    gespraechSeit,
                    verpasst: false,
                    eingegangen: bisher?.eingegangen ?? Date.now(),
                    geschlossen: bisher?.geschlossen ?? false,
                },
            };
        });
    }, [entferne]);

    useEffect(() => {
        const timer = timerRef.current;
        return () => {
            timer.forEach(clearTimeout);
            timer.clear();
        };
    }, []);

    useEffect(() => {
        if (!aktiv) return;
        let quelle: EventSource | null = null;
        let wiederTimer: ReturnType<typeof setTimeout> | null = null;
        let wartezeit = ERSTE_WARTEZEIT_MS;
        let beendet = false;

        const beiAnruf = (ereignis: MessageEvent) => {
            const anruf = liesAnruf(String(ereignis.data));
            if (anruf) verarbeite(anruf);
        };

        const verbinde = () => {
            if (beendet) return;
            quelle = fabrik('/api/telefon/live');
            quelle.addEventListener('anruf', beiAnruf as EventListener);
            quelle.onopen = () => {
                wartezeit = ERSTE_WARTEZEIT_MS;
            };
            quelle.onerror = () => {
                // CONNECTING: der Browser verbindet selbst neu. CLOSED: er hat
                // aufgegeben (z. B. Server-Fehler) – dann übernehmen wir.
                if (!quelle || quelle.readyState !== 2 /* EventSource.CLOSED */) return;
                quelle.close();
                quelle = null;
                wiederTimer = setTimeout(verbinde, wartezeit);
                wartezeit = Math.min(wartezeit * 2, LAENGSTE_WARTEZEIT_MS);
            };
        };

        verbinde();
        return () => {
            beendet = true;
            if (wiederTimer) clearTimeout(wiederTimer);
            if (quelle) {
                quelle.removeEventListener('anruf', beiAnruf as EventListener);
                quelle.close();
            }
        };
    }, [aktiv, fabrik, verarbeite]);

    /** Blendet den Anruf für diesen Benutzer aus; spätere Ereignisse holen ihn nicht zurück. */
    const schliessen = useCallback((verbindungsId: string) => {
        setEintraege((alt) => {
            const bisher = alt[verbindungsId];
            if (!bisher) return alt;
            if (bisher.verpasst) {
                const neu = { ...alt };
                delete neu[verbindungsId];
                return neu;
            }
            return { ...alt, [verbindungsId]: { ...bisher, geschlossen: true } };
        });
    }, []);

    /**
     * Hält einen verpassten Anruf offen, bis er geschlossen wird – statt ihn
     * nach drei Sekunden auszublenden. Für „Zurückrufen" aus dem Anruf-Fenster.
     */
    const festhalten = useCallback((verbindungsId: string) => {
        const timer = timerRef.current.get(verbindungsId);
        if (!timer) return;
        clearTimeout(timer);
        timerRef.current.delete(verbindungsId);
    }, []);

    // Ohne Recht (oder nach dem Entzug) wird nichts gezeigt, auch keine Reste.
    const anrufe: LiveAnrufAnzeige[] = !aktiv ? [] : Object.values(eintraege)
        .filter((e) => !e.geschlossen)
        .sort((a, b) => b.eingegangen - a.eingegangen)
        .map((e) => ({
            verbindungsId: e.verbindungsId,
            status: e.status,
            nummer: e.nummer,
            kontakt: e.kontakt,
            kandidaten: e.kandidaten,
            angenommen: e.angenommen,
            gespraechSeit: e.gespraechSeit,
            verpasst: e.verpasst,
        }));

    return { anrufe, schliessen, festhalten };
}
