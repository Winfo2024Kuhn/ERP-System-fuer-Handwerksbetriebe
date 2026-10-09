import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DokumentPosition } from '../../components/zuordnung/positionen';
import {
    POSITIONEN_FEHLER_TEXT,
    gemerktePositionen,
    kannPositionenHaben,
    ladeDokumentPositionen,
    positionTrifftSuche,
    vergissDokumentPositionen,
} from './dokumentPositionen';

function position(id: number, extra: Partial<DokumentPosition> = {}): DokumentPosition {
    return {
        id, positionNr: id, positionsArt: 'WARE', externeArtikelnummer: `ART-${id}`, bezeichnung: `Flachstahl 50x5 Nr. ${id}`,
        menge: 12, mengeneinheit: 'Stk', einzelpreis: 3.5, preiseinheit: null, gesamtpreisNetto: 42,
        projektId: null, projektName: null, kostenstelleId: null, kostenstelleName: null, ...extra,
    };
}

const antwort = (body: unknown, status = 200) => ({ ok: status < 400, status, json: async () => body }) as Response;

describe('ladeDokumentPositionen', () => {
    beforeEach(() => {
        vergissDokumentPositionen();
        vi.stubGlobal('fetch', vi.fn());
    });
    afterEach(() => vi.unstubAllGlobals());

    it('lädt einmal, merkt sich Positionen und teilt gleichzeitige Abrufe', async () => {
        vi.mocked(fetch).mockResolvedValueOnce(antwort({ positionen: [position(1)] }));
        const [a, b] = await Promise.all([ladeDokumentPositionen(7), ladeDokumentPositionen(7)]);
        expect(a).toEqual([position(1)]);
        expect(b).toBe(a);
        expect(await ladeDokumentPositionen(7)).toBe(a);
        expect(gemerktePositionen(7)).toBe(a);
        expect(fetch).toHaveBeenCalledTimes(1);
        expect(vi.mocked(fetch).mock.calls[0][0]).toBe('/api/bestellungen-uebersicht/positionen/7');
    });

    it('wertet 404 und fehlende Liste als „keine Positionen“ und merkt sich leere Ergebnisse nicht', async () => {
        vi.mocked(fetch).mockResolvedValueOnce(antwort({}, 404)).mockResolvedValueOnce(antwort({ positionen: 'kaputt' }));
        expect(await ladeDokumentPositionen(8)).toEqual([]);
        expect(gemerktePositionen(8)).toBeUndefined();
        expect(await ladeDokumentPositionen(8)).toEqual([]);
        expect(fetch).toHaveBeenCalledTimes(2);
    });

    it('meldet andere Fehler mit eigenem Text und versucht es beim nächsten Mal erneut', async () => {
        vi.mocked(fetch).mockResolvedValueOnce(antwort({}, 500)).mockResolvedValueOnce(antwort({ positionen: [position(2)] }));
        await expect(ladeDokumentPositionen(9)).rejects.toThrow(POSITIONEN_FEHLER_TEXT);
        expect(await ladeDokumentPositionen(9)).toEqual([position(2)]);
    });

    it('vergisst ein einzelnes Dokument und lädt es danach neu, andere bleiben gemerkt', async () => {
        vi.mocked(fetch)
            .mockResolvedValueOnce(antwort({ positionen: [position(1)] }))
            .mockResolvedValueOnce(antwort({ positionen: [position(2)] }))
            .mockResolvedValueOnce(antwort({ positionen: [position(3)] }));
        await ladeDokumentPositionen(10);
        await ladeDokumentPositionen(11);

        vergissDokumentPositionen(10);

        expect(gemerktePositionen(10)).toBeUndefined();
        expect(gemerktePositionen(11)).toEqual([position(2)]);
        expect(await ladeDokumentPositionen(10)).toEqual([position(3)]);
        expect(fetch).toHaveBeenCalledTimes(3);
    });

    it('merkt sich keine Antwort, die vor dem Vergessen losgeschickt wurde', async () => {
        let antworten: (r: Response) => void = () => {};
        vi.mocked(fetch).mockReturnValueOnce(new Promise<Response>(r => { antworten = r; }));
        const alt = ladeDokumentPositionen(12);

        vergissDokumentPositionen(12);
        antworten(antwort({ positionen: [position(1)] }));

        expect(await alt).toEqual([position(1)]);
        expect(gemerktePositionen(12)).toBeUndefined();
    });

    it('vergisst beim Leeren ohne Angabe alles, auch laufende Abrufe', async () => {
        let antworten: (r: Response) => void = () => {};
        vi.mocked(fetch)
            .mockResolvedValueOnce(antwort({ positionen: [position(1)] }))
            .mockReturnValueOnce(new Promise<Response>(r => { antworten = r; }));
        await ladeDokumentPositionen(13);
        const laufend = ladeDokumentPositionen(14);

        vergissDokumentPositionen();
        antworten(antwort({ positionen: [position(2)] }));
        await laufend;

        expect(gemerktePositionen(13)).toBeUndefined();
        expect(gemerktePositionen(14)).toBeUndefined();
    });
});

describe('kannPositionenHaben', () => {
    it('bietet bei allem außer Sonstigem Positionen an', () => {
        expect(kannPositionenHaben('RECHNUNG')).toBe(true);
        expect(kannPositionenHaben('WERKSTOFFZEUGNIS')).toBe(true);
        expect(kannPositionenHaben(null)).toBe(true);
        expect(kannPositionenHaben('SONSTIG')).toBe(false);
    });
});

describe('positionTrifftSuche', () => {
    const zeugnis = position(3, { bezeichnung: 'Flachstahl', externeArtikelnummer: null, werkstoff: 'S235JR', charge: '123456', abmessung: '50 x 5' });

    it('findet alle Suchwörter in Bezeichnung, Artikelnummer, Werkstoff, Charge und Abmessung', () => {
        expect(positionTrifftSuche(zeugnis, 'flachstahl s235jr')).toBe(true);
        expect(positionTrifftSuche(zeugnis, '50x5')).toBe(true);
        expect(positionTrifftSuche(zeugnis, 'Charge 999999')).toBe(false);
        expect(positionTrifftSuche(position(4), 'art-4')).toBe(true);
    });

    it('trifft ohne brauchbaren Suchbegriff nichts', () => {
        expect(positionTrifftSuche(zeugnis, '')).toBe(false);
        expect(positionTrifftSuche(zeugnis, undefined)).toBe(false);
        expect(positionTrifftSuche(zeugnis, 'f')).toBe(false);
    });
});
