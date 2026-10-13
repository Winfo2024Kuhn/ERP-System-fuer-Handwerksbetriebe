import { useEffect, useState } from 'react';
import type { VorjahresRest } from '../lib/urlaubstage';

/** Resturlaub eines Mitarbeiters in einem Jahr, wie ihn `/api/urlaub/resturlaub` liefert. */
export interface Resturlaub extends VorjahresRest {
    verbleibend: number;
    jahr: number;
}

export interface AntragFuerResturlaub {
    id: number;
    status: string;
    typ: string;
    vonDatum: string;
    mitarbeiter: { id: number };
}

/** Schlüssel je Mitarbeiter und Jahr des Urlaubsbeginns. */
export const resturlaubSchluessel = (antrag: AntragFuerResturlaub) =>
    `${antrag.mitarbeiter.id}:${antrag.vonDatum.slice(0, 4)}`;

function istResturlaub(wert: unknown): wert is Resturlaub {
    return !!wert && typeof wert === 'object' && typeof (wert as Resturlaub).verbleibend === 'number';
}

/**
 * Lädt für offene Urlaubsanträge den Resturlaub des Mitarbeiters im Jahr des
 * Urlaubs – inklusive Rest aus dem Vorjahr –, damit das Büro beim Genehmigen
 * sieht, was noch übrig ist. Je Mitarbeiter und Jahr genau eine Anfrage, alle
 * gemeinsam (kein Wasserfall). Fehler blenden die Angabe nur aus: Die
 * Antragsliste bleibt bedienbar, die Prüfung macht der Server beim Antrag.
 */
export function useResturlaubJeAntrag(antraege: AntragFuerResturlaub[]): Record<string, Resturlaub> {
    const [resturlaub, setResturlaub] = useState<Record<string, Resturlaub>>({});

    useEffect(() => {
        const schluessel = [...new Set(antraege
            .filter(a => a.status === 'OFFEN' && a.typ === 'URLAUB' && a.mitarbeiter && a.vonDatum)
            .map(resturlaubSchluessel))];
        // Ohne offene Urlaubsanträge nichts laden; alte Einträge stören nicht, weil
        // die Seite nur bei offenen Urlaubsanträgen nachschlägt.
        if (schluessel.length === 0) return;
        let abgebrochen = false;
        Promise.all(schluessel.map(async (eintrag): Promise<[string, Resturlaub | null]> => {
            const [mitarbeiterId, jahr] = eintrag.split(':');
            try {
                const params = new URLSearchParams({ mitarbeiterId, jahr });
                const res = await fetch(`/api/urlaub/resturlaub?${params.toString()}`);
                if (!res.ok) return [eintrag, null];
                const daten: unknown = await res.json();
                return [eintrag, istResturlaub(daten) ? daten : null];
            } catch {
                return [eintrag, null];
            }
        })).then(ergebnisse => {
            if (abgebrochen) return;
            setResturlaub(Object.fromEntries(ergebnisse.filter((e): e is [string, Resturlaub] => e[1] !== null)));
        });
        return () => { abgebrochen = true; };
    }, [antraege]);

    return resturlaub;
}
