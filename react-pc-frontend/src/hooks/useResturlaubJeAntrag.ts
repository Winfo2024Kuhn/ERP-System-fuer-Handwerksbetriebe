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

/** Schlüssel je Mitarbeiter und erstem Urlaubstag (= Stichtag der Abfrage). */
export const resturlaubSchluessel = (antrag: AntragFuerResturlaub) =>
    `${antrag.mitarbeiter.id}:${antrag.vonDatum.slice(0, 10)}`;

function istResturlaub(wert: unknown): wert is Resturlaub {
    return !!wert && typeof wert === 'object' && typeof (wert as Resturlaub).verbleibend === 'number';
}

/**
 * Lädt für offene Urlaubsanträge den Resturlaub des Mitarbeiters zum ersten
 * Urlaubstag – inklusive Rest aus dem Vorjahr, der nach dem Verfallstag schon
 * weg ist –, damit das Büro beim Genehmigen sieht, was für diesen Antrag noch
 * übrig ist. Je Mitarbeiter und Stichtag genau eine Anfrage, alle
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
            const [mitarbeiterId, stichtag] = eintrag.split(':');
            try {
                const params = new URLSearchParams({ mitarbeiterId, jahr: stichtag.slice(0, 4), stichtag });
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
