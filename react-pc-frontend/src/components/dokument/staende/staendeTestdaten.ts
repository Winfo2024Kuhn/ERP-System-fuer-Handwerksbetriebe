import type { StaendeSeite, StandEintrag } from './staendeTypen';

/** Testdaten für Stände (DSGVO: nur Musternamen). */
export function standEintrag(teil: Partial<StandEintrag> = {}): StandEintrag {
    return {
        id: 3,
        nummer: 3,
        art: 'EXPORT',
        festgehaltenAm: '2026-11-12T14:32:00',
        festgehaltenVon: 'Max Mustermann',
        bearbeitetVon: ['Erika Musterfrau'],
        betragNetto: 1234.5,
        aenderungen: ['Pos. 3 „Geländer Edelstahl“ gelöscht', 'Rabatt 5 % gegeben'],
        wiederhergestelltAusNummer: null,
        aktuell: false,
        pdfVorhanden: true,
        ...teil,
    };
}

export function staendeSeite(teil: Partial<StaendeSeite> = {}): StaendeSeite {
    const staende = teil.staende ?? [standEintrag()];
    return {
        staende,
        gesamt: staende.length,
        seite: 0,
        seitenGroesse: 25,
        hatMehr: false,
        offeneAenderungen: [],
        dokumentBearbeitbar: true,
        ...teil,
    };
}

export function jsonAntwort(status: number, body: unknown): Response {
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}
