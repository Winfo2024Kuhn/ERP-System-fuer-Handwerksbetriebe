import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ordnerKette, type Baum } from './ordnerLogik';
import type { LadeStatus } from './useLeistungenDaten';
import type { Leistung, Ordner, ReiterId } from './typen';

const REITER: ReiterId[] = ['leistungen', 'ordner', 'auswertung'];

/** Ordner-ID aus der Adresse (`?ordner=12`), sonst `null` (= Übersicht). */
export function ordnerAusSuche(suche: string): number | null {
    const wert = new URLSearchParams(suche).get('ordner');
    const zahl = wert ? Number(wert) : NaN;
    return Number.isInteger(zahl) && zahl > 0 ? zahl : null;
}

/**
 * Auswahl auf der Seite „Leistungen & Kategorien“: gewählter Ordner und Reiter
 * stehen in der Adresse (`?ordner=12&reiter=auswertung`), damit Zurück und
 * Links gehen. Dazu der Aufklapp-Zustand des Baums und „Leistung öffnen“ aus der Suche.
 */
export function useOrdnerAuswahl({ baum, ordnerRoh, ladeStatus }: { baum: Baum; ordnerRoh: Ordner[]; ladeStatus: LadeStatus }) {
    const [params, setParams] = useSearchParams();
    const gewaehlt = ordnerAusSuche(params.toString());
    const reiterWert = params.get('reiter') as ReiterId | null;
    const reiter: ReiterId = reiterWert && REITER.includes(reiterWert) ? reiterWert : 'leistungen';
    const gewaehlterOrdner = gewaehlt !== null ? baum.byId.get(gewaehlt) ?? null : null;
    const [aufgeklappt, setAufgeklappt] = useState<Set<number>>(() => new Set());
    const [oeffneLeistung, setOeffneLeistung] = useState<{ id: number; zaehler: number } | null>(null);

    const waehle = useCallback((id: number | null, neuerReiter?: ReiterId) => {
        setParams((alt) => {
            const neu = new URLSearchParams(alt);
            if (id === null) { neu.delete('ordner'); neu.delete('reiter'); } else {
                neu.set('ordner', String(id));
                const r = neuerReiter ?? (alt.get('reiter') as ReiterId | null) ?? 'leistungen';
                if (r === 'leistungen') neu.delete('reiter'); else neu.set('reiter', r);
            }
            return neu;
        });
    }, [setParams]);

    const aufklappen = useCallback((id: number, offen: boolean) => {
        setAufgeklappt((alt) => {
            const neu = new Set(alt);
            if (offen) neu.add(id); else neu.delete(id);
            return neu;
        });
    }, []);

    // Gewählten Ordner im Baum sichtbar machen und aufklappen – einmal je Auswahl, danach darf man ihn zuklappen.
    const zuletztAufgeklappt = useRef<number | null>(null);
    useEffect(() => {
        if (gewaehlt === null || gewaehlt === zuletztAufgeklappt.current || !baum.byId.has(gewaehlt)) return;
        zuletztAufgeklappt.current = gewaehlt;
        const kette = ordnerKette(baum, gewaehlt).map((o) => o.id);
        // Erlaubt im Effekt: hängt am Merker-Ref, läuft also nur einmal je Auswahl.
        setAufgeklappt((alt) => new Set([...alt, ...kette]));
    }, [gewaehlt, baum]);

    // Unbekannter Ordner in der Adresse (z. B. inzwischen gelöscht) → zurück zur Übersicht.
    useEffect(() => {
        if (ladeStatus !== 'fertig' || gewaehlt === null || gewaehlterOrdner) return;
        if (ordnerRoh.some((o) => o.id === gewaehlt)) return; // nur ausgeblendet (Löschen läuft)
        setParams((alt) => { const neu = new URLSearchParams(alt); neu.delete('ordner'); neu.delete('reiter'); return neu; }, { replace: true });
    }, [ladeStatus, gewaehlt, gewaehlterOrdner, ordnerRoh, setParams]);

    // Treffer aus der Suche: Ordner wählen und die Leistung dort im Formular öffnen (Zähler erzwingt das auch beim zweiten Mal).
    const zaehlerRef = useRef(0);
    const leistungOeffnen = (leistung: Leistung) => {
        if (leistung.ordnerId === null) return;
        zaehlerRef.current += 1;
        setOeffneLeistung({ id: leistung.id, zaehler: zaehlerRef.current });
        waehle(leistung.ordnerId, 'leistungen');
    };

    return { gewaehlt, gewaehlterOrdner, reiter, waehle, aufgeklappt, aufklappen, oeffneLeistung, leistungOeffnen };
}
