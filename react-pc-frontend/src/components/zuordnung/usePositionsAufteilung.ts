import { useCallback, useEffect, useMemo, useState } from 'react';
import {
    baueAufteilung,
    offeneWarenpositionen,
    zuweisungenAusPositionen,
    type DokumentPosition,
    type PositionsUebersicht,
    type PositionsVorschau,
    type ZielDetail,
    type ZielSchluessel,
} from './positionen';

const BASIS = '/api/bestellungen-uebersicht/positionen';
const VORSCHAU_VERZOEGERUNG_MS = 300;

/** Liest die Fehlermeldung `{ error }` des Backends, sonst den Ersatztext. */
async function fehlertext(res: Response, ersatz: string): Promise<string> {
    try {
        const body = await res.json();
        if (body && typeof body.error === 'string' && body.error.trim()) return body.error;
    } catch { /* kein JSON */ }
    return ersatz;
}

export interface PositionsAufteilung {
    /** Positionen des Dokuments; `null` solange nicht geladen oder bei Fehler. */
    uebersicht: PositionsUebersicht | null;
    positionen: DokumentPosition[];
    /** Modus „Nach Positionen“ ist sinnvoll: Positionen da oder auslesbar. */
    verfuegbar: boolean;
    laden: () => Promise<PositionsUebersicht | null>;

    /** Liest die Positionen per KI; `vorbelegung` gibt allen Warenpositionen gleich dieses Ziel. */
    auslesen: (vorbelegung?: ZielSchluessel) => Promise<PositionsUebersicht | null>;
    auslesenLaeuft: boolean;
    auslesenFehler: string | null;

    zuweisungen: Record<number, ZielSchluessel>;
    weiseZu: (positionIds: number[], ziel: ZielSchluessel) => void;
    /** Alle noch offenen Warenpositionen bekommen dieses Ziel. */
    weiseOffeneZu: (ziel: ZielSchluessel) => void;
    /** Nimmt ein entferntes Ziel von allen Positionen wieder herunter. */
    entferneZiel: (ziel: ZielSchluessel) => void;
    anzahlOffen: number;
    anzahlWare: number;

    vorschau: PositionsVorschau | null;
    vorschauLaeuft: boolean;
    vorschauFehler: string | null;
    /** Vorschau passt zur aktuellen Auswahl und das Backend erlaubt Speichern. */
    speicherbar: boolean;
    speichern: (ziele: ZielDetail[]) => Promise<void>;
}

/**
 * Laden, Auslesen, Live-Vorschau und Speichern der Aufteilung nach Positionen.
 * Die Vorschau läuft nur, solange `aktiv` gesetzt ist (Modus gewählt).
 */
export function usePositionsAufteilung(geschaeftsdokumentId: number | null | undefined, aktiv: boolean): PositionsAufteilung {
    const [uebersicht, setUebersicht] = useState<PositionsUebersicht | null>(null);
    const [zuweisungen, setZuweisungen] = useState<Record<number, ZielSchluessel>>({});
    const [auslesenLaeuft, setAuslesenLaeuft] = useState(false);
    const [auslesenFehler, setAuslesenFehler] = useState<string | null>(null);
    const [vorschau, setVorschau] = useState<{ schluessel: string; daten: PositionsVorschau } | null>(null);
    const [vorschauLaeuft, setVorschauLaeuft] = useState(false);
    const [vorschauFehler, setVorschauFehler] = useState<string | null>(null);

    const positionen = useMemo(() => uebersicht?.positionen ?? [], [uebersicht]);

    const uebernehmen = useCallback((daten: PositionsUebersicht, vorbelegung?: ZielSchluessel) => {
        const sauber = { ...daten, positionen: Array.isArray(daten.positionen) ? daten.positionen : [] };
        const gespeichert = zuweisungenAusPositionen(sauber.positionen);
        if (vorbelegung) {
            for (const p of offeneWarenpositionen(sauber.positionen, gespeichert)) gespeichert[p.id] = vorbelegung;
        }
        setUebersicht(sauber);
        setZuweisungen(gespeichert);
        return sauber;
    }, []);

    const laden = useCallback(async () => {
        if (geschaeftsdokumentId == null) return null;
        const res = await fetch(`${BASIS}/${geschaeftsdokumentId}`);
        if (res.status === 404) {
            setUebersicht(null);
            return null;
        }
        if (!res.ok) throw new Error(await fehlertext(res, 'Positionen konnten nicht geladen werden.'));
        return uebernehmen(await res.json());
    }, [geschaeftsdokumentId, uebernehmen]);

    const auslesen = useCallback(async (vorbelegung?: ZielSchluessel) => {
        if (geschaeftsdokumentId == null) return null;
        setAuslesenLaeuft(true);
        setAuslesenFehler(null);
        try {
            const res = await fetch(`${BASIS}/${geschaeftsdokumentId}/auslesen`, { method: 'POST' });
            if (!res.ok) throw new Error(await fehlertext(res, 'Die Positionen konnten nicht ausgelesen werden.'));
            return uebernehmen(await res.json(), vorbelegung);
        } catch (err) {
            const meldung = err instanceof Error && err.message && err.message !== 'Failed to fetch'
                ? err.message
                : 'Die Positionen konnten nicht ausgelesen werden.';
            setAuslesenFehler(meldung);
            throw new Error(meldung);
        } finally {
            setAuslesenLaeuft(false);
        }
    }, [geschaeftsdokumentId, uebernehmen]);

    const weiseZu = useCallback((positionIds: number[], ziel: ZielSchluessel) => {
        setZuweisungen(alt => {
            const neu = { ...alt };
            for (const id of positionIds) {
                if (ziel) neu[id] = ziel;
                else delete neu[id];
            }
            return neu;
        });
    }, []);

    const weiseOffeneZu = useCallback((ziel: ZielSchluessel) => {
        if (!ziel) return;
        setZuweisungen(alt => {
            const neu = { ...alt };
            for (const p of offeneWarenpositionen(positionen, alt)) neu[p.id] = ziel;
            return neu;
        });
    }, [positionen]);

    const entferneZiel = useCallback((ziel: ZielSchluessel) => {
        setZuweisungen(alt => Object.fromEntries(Object.entries(alt).filter(([, z]) => z !== ziel)));
    }, []);

    const anzahlWare = useMemo(() => positionen.filter(p => p.positionsArt === 'WARE').length, [positionen]);
    const anzahlOffen = useMemo(() => offeneWarenpositionen(positionen, zuweisungen).length, [positionen, zuweisungen]);

    // Request für Vorschau/Speichern; der JSON-Text dient zugleich als Vergleichsschlüssel.
    const aufteilung = useMemo(() => baueAufteilung(positionen, zuweisungen), [positionen, zuweisungen]);
    const aufteilungsSchluessel = useMemo(() => JSON.stringify(aufteilung), [aufteilung]);

    // Live-Vorschau, entprellt; veraltete Antworten werden verworfen.
    useEffect(() => {
        if (!aktiv || geschaeftsdokumentId == null || positionen.length === 0) return;
        const abbruch = new AbortController();
        setVorschauLaeuft(true);
        const timer = window.setTimeout(async () => {
            try {
                const res = await fetch(`${BASIS}/${geschaeftsdokumentId}/vorschau`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ positionen: JSON.parse(aufteilungsSchluessel) }),
                    signal: abbruch.signal,
                });
                if (!res.ok) throw new Error(await fehlertext(res, 'Die Aufteilung konnte nicht berechnet werden.'));
                const daten: PositionsVorschau = await res.json();
                if (abbruch.signal.aborted) return;
                setVorschau({ schluessel: aufteilungsSchluessel, daten: { ...daten, ziele: Array.isArray(daten.ziele) ? daten.ziele : [] } });
                setVorschauFehler(null);
            } catch (err) {
                if (abbruch.signal.aborted) return;
                const meldung = err instanceof Error && err.message && err.message !== 'Failed to fetch'
                    ? err.message
                    : 'Die Aufteilung konnte nicht berechnet werden.';
                setVorschauFehler(meldung);
            } finally {
                if (!abbruch.signal.aborted) setVorschauLaeuft(false);
            }
        }, VORSCHAU_VERZOEGERUNG_MS);
        return () => {
            window.clearTimeout(timer);
            abbruch.abort();
            setVorschauLaeuft(false);
        };
    }, [aktiv, geschaeftsdokumentId, positionen.length, aufteilungsSchluessel]);

    const aktuelleVorschau = vorschau?.schluessel === aufteilungsSchluessel ? vorschau.daten : null;
    const speicherbar = !!aktuelleVorschau?.speicherbar && !vorschauLaeuft && anzahlOffen === 0;

    const speichern = useCallback(async (ziele: ZielDetail[]) => {
        if (geschaeftsdokumentId == null) throw new Error('Zuordnungsziel fehlt');
        const res = await fetch(`${BASIS}/${geschaeftsdokumentId}/zuordnen`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ positionen: aufteilung, ziele }),
        });
        if (!res.ok) throw new Error(await fehlertext(res, 'Die Aufteilung konnte nicht gespeichert werden.'));
    }, [geschaeftsdokumentId, aufteilung]);

    const verfuegbar = uebersicht != null && (uebersicht.auslesbar || positionen.length > 0);

    return {
        uebersicht,
        positionen,
        verfuegbar,
        laden,
        auslesen,
        auslesenLaeuft,
        auslesenFehler,
        zuweisungen,
        weiseZu,
        weiseOffeneZu,
        entferneZiel,
        anzahlOffen,
        anzahlWare,
        // Letzte Vorschau weiter zeigen, während die neue rechnet – sonst flackern die Beträge.
        vorschau: aktuelleVorschau ?? vorschau?.daten ?? null,
        vorschauLaeuft,
        vorschauFehler,
        speicherbar,
        speichern,
    };
}
