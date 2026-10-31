import { useCallback, useEffect, useRef, useState } from 'react';
import { IdsApiFehler, ladeIdsVorgang, starteIdsVorgang, type IdsVorgang } from './api';
import { sendeShopFormular, zeigeWarteTextImTab } from './shopFormular';

/**
 * Ein Besuch im Lieferanten-Shop: Vorgang anlegen, Shop im neuen Tab öffnen,
 * auf den Warenkorb warten.
 *
 * <p>Fachneutral – der Hook kennt keine Dokumente. Er fragt alle 3 Sekunden
 * nach, ob der Warenkorb angekommen ist, und hört zusätzlich auf den Kanal
 * `ids-warenkorb`: Die Rücksprung-Seite im Shop-Tab meldet sich dort sofort,
 * dann muss niemand auf die nächste Abfrage warten. Nach 60 Minuten ist der
 * Besuch auch beim Server abgelaufen; dann hört der Hook von selbst auf.</p>
 */

export const ABFRAGE_ALLE_MS = 3000;
export const HOECHSTDAUER_MS = 60 * 60 * 1000;
export const IDS_KANAL = 'ids-warenkorb';

export type ShopBesuchPhase = 'bereit' | 'startet' | 'wartet' | 'empfangen' | 'abgelaufen' | 'fehler';

export interface ShopBesuchZustand {
    phase: ShopBesuchPhase;
    /** Der zuletzt gelesene Stand des Vorgangs (bei „empfangen“ mit Positionen). */
    vorgang: IdsVorgang | null;
    /** Verständlicher Satz, wenn etwas schiefging. */
    fehlertext: string | null;
}

const START: ShopBesuchZustand = { phase: 'bereit', vorgang: null, fehlertext: null };

export const POPUP_BLOCKIERT = 'Der Browser hat das neue Fenster blockiert – bitte Pop-ups für dieses Programm erlauben.';
const ABGELAUFEN_TEXT = 'Der Shop-Besuch war länger als 60 Minuten offen. Bitte den Shop erneut öffnen.';

export function useIdsShopBesuch() {
    const [zustand, setZustand] = useState<ShopBesuchZustand>(START);
    // Jeder Start bekommt eine neue Nummer; Antworten eines alten Besuchs werden verworfen.
    const laufRef = useRef(0);
    const vorgangIdRef = useRef<number | null>(null);
    const startZeitRef = useRef(0);
    const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    // Läuft gerade eine Abfrage? Dann keine zweite parallel (Kanal-Meldung + Zeitgeber).
    const fragtRef = useRef(false);
    // Zeigt immer auf die aktuelle Abfrage – so kann sie sich selbst neu einplanen.
    const naechsteAbfrageRef = useRef<(lauf: number) => void>(() => undefined);

    const stoppeTimer = useCallback(() => {
        if (timerRef.current) clearTimeout(timerRef.current);
        timerRef.current = null;
    }, []);

    const beende = useCallback((neu: ShopBesuchZustand) => {
        stoppeTimer();
        vorgangIdRef.current = null;
        setZustand(neu);
    }, [stoppeTimer]);

    const frageAb = useCallback(async (lauf: number) => {
        const vorgangId = vorgangIdRef.current;
        if (vorgangId == null || lauf !== laufRef.current || fragtRef.current) return;
        stoppeTimer();
        if (Date.now() - startZeitRef.current >= HOECHSTDAUER_MS) {
            beende({ phase: 'abgelaufen', vorgang: null, fehlertext: ABGELAUFEN_TEXT });
            return;
        }
        fragtRef.current = true;
        try {
            const vorgang = await ladeIdsVorgang(vorgangId);
            if (lauf !== laufRef.current) return;
            switch (vorgang.status) {
                case 'EMPFANGEN':
                    beende({ phase: 'empfangen', vorgang, fehlertext: null });
                    return;
                case 'ABGELAUFEN':
                    beende({ phase: 'abgelaufen', vorgang, fehlertext: vorgang.fehlertext || ABGELAUFEN_TEXT });
                    return;
                case 'FEHLER':
                    beende({ phase: 'fehler', vorgang, fehlertext: vorgang.fehlertext || 'Der Warenkorb konnte nicht gelesen werden. Bitte erneut versuchen.' });
                    return;
                default:
                    setZustand((alt) => ({ ...alt, vorgang }));
            }
        } catch (e) {
            if (lauf !== laufRef.current) return;
            if (e instanceof IdsApiFehler && e.status === 404) {
                beende({ phase: 'fehler', vorgang: null, fehlertext: e.message || 'Dieser Shop-Besuch ist nicht mehr bekannt. Bitte den Shop erneut öffnen.' });
                return;
            }
            // Kurzer Netz-Aussetzer: einfach beim nächsten Mal wieder fragen.
        } finally {
            fragtRef.current = false;
        }
        timerRef.current = setTimeout(() => naechsteAbfrageRef.current(lauf), ABFRAGE_ALLE_MS);
    }, [beende, stoppeTimer]);

    useEffect(() => {
        naechsteAbfrageRef.current = (lauf: number) => void frageAb(lauf);
    }, [frageAb]);

    /**
     * Öffnet den Shop. MUSS direkt im Klick-Handler aufgerufen werden: Der Tab
     * wird sofort (synchron) geöffnet, sonst blockt der Browser ihn.
     */
    const starte = useCallback((lieferantId: number) => {
        const lauf = laufRef.current + 1;
        laufRef.current = lauf;
        stoppeTimer();
        vorgangIdRef.current = null;
        const tab = window.open('about:blank', '_blank');
        if (!tab) {
            setZustand({ phase: 'fehler', vorgang: null, fehlertext: POPUP_BLOCKIERT });
            return;
        }
        zeigeWarteTextImTab(tab, 'Shop wird geöffnet …');
        setZustand({ phase: 'startet', vorgang: null, fehlertext: null });
        void (async () => {
            try {
                // Das Formular (mit Passwort) lebt nur in dieser Funktion – nie im Zustand.
                const start = await starteIdsVorgang(lieferantId);
                if (lauf !== laufRef.current) {
                    tab.close();
                    return;
                }
                sendeShopFormular(tab, start.formular);
                vorgangIdRef.current = start.vorgangId;
                startZeitRef.current = Date.now();
                setZustand({ phase: 'wartet', vorgang: null, fehlertext: null });
                timerRef.current = setTimeout(() => void frageAb(lauf), ABFRAGE_ALLE_MS);
            } catch (e) {
                tab.close();
                if (lauf !== laufRef.current) return;
                setZustand({
                    phase: 'fehler',
                    vorgang: null,
                    fehlertext: e instanceof Error && e.message ? e.message : 'Der Shop konnte nicht geöffnet werden.',
                });
            }
        })();
    }, [frageAb, stoppeTimer]);

    /** Hört auf zu warten (z. B. beim Schließen des Dialogs) und setzt alles zurück. */
    const abbrechen = useCallback(() => {
        laufRef.current += 1;
        beende(START);
    }, [beende]);

    // Die Rücksprung-Seite meldet „Warenkorb da“ – dann sofort nachfragen statt bis zu 3 s zu warten.
    useEffect(() => {
        if (typeof BroadcastChannel === 'undefined') return;
        const kanal = new BroadcastChannel(IDS_KANAL);
        kanal.onmessage = (event: MessageEvent<{ vorgangId?: unknown }>) => {
            const id = event.data?.vorgangId;
            if (id != null && Number(id) === vorgangIdRef.current) void frageAb(laufRef.current);
        };
        return () => kanal.close();
    }, [frageAb]);

    // Beim Verlassen nicht weiter nachfragen.
    useEffect(() => () => {
        laufRef.current += 1;
        stoppeTimer();
    }, [stoppeTimer]);

    return { ...zustand, starte, abbrechen };
}
