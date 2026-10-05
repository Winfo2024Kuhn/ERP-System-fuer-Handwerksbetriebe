import { afterEach, describe, expect, it, vi } from 'vitest';
import {
    brauchtRueckfrage,
    formatiereQuote,
    ladeRechnungsVorschlaege,
    passtZurRechnungsSuche,
    rechnungVerknuepfen,
    rueckfrageText,
    trefferStufe,
    type RechnungsVorschlag,
} from './rechnungsVorschlag';

const vorschlag: RechnungsVorschlag = {
    rechnung: {
        id: 7, typ: 'RECHNUNG', dokumentNummer: 'RE-123', dokumentDatum: '2026-09-01', betragBrutto: 119, betragNetto: 100,
        liefertermin: null, dateiname: 're.pdf', pdfUrl: '/api/x.pdf',
    },
    lieferantName: 'Erika Musterfrau KG', bestellDokumentId: 3, bestellDokumentTyp: 'AUFTRAGSBESTAETIGUNG',
    bestellDokumentNummer: 'AB-1', trefferquote: 82, sicher: true, eindeutig: true, gruende: ['Gleicher Lieferant'],
};

afterEach(() => vi.unstubAllGlobals());

describe('brauchtRueckfrage / rueckfrageText', () => {
    it('fragt bei niedriger Quote oder Gleichstand nach', () => {
        expect(brauchtRueckfrage(vorschlag)).toBe(false);
        expect(brauchtRueckfrage({ ...vorschlag, trefferquote: 69 })).toBe(true);
        expect(brauchtRueckfrage({ ...vorschlag, eindeutig: false })).toBe(true);
    });

    it('nennt den Grund und die Rechnung', () => {
        expect(rueckfrageText({ ...vorschlag, trefferquote: 45 })).toContain('nur bei 45 %');
        expect(rueckfrageText({ ...vorschlag, eindeutig: false })).toContain('Eine andere Rechnung passt genauso gut.');
        expect(rueckfrageText(vorschlag)).toContain('Rechnung RE-123');
        expect(rueckfrageText({ ...vorschlag, rechnung: { ...vorschlag.rechnung, dokumentNummer: null } })).toContain('Rechnung re.pdf');
    });
});

describe('trefferStufe / formatiereQuote', () => {
    it('stuft nach Schwellen ein', () => {
        expect(trefferStufe(100)).toBe('hoch');
        expect(trefferStufe(70)).toBe('hoch');
        expect(trefferStufe(69.9)).toBe('mittel');
        expect(trefferStufe(40)).toBe('mittel');
        expect(trefferStufe(39)).toBe('niedrig');
    });
    it('rundet die Quote', () => {
        expect(formatiereQuote(81.6)).toBe('82 %');
    });
});

describe('passtZurRechnungsSuche', () => {
    it('sucht in Nummer, Lieferant, Betrag und Datum', () => {
        expect(passtZurRechnungsSuche(vorschlag, 're-123')).toBe(true);
        expect(passtZurRechnungsSuche(vorschlag, 'musterfrau')).toBe(true);
        expect(passtZurRechnungsSuche(vorschlag, '119,00')).toBe(true);
        expect(passtZurRechnungsSuche(vorschlag, '01.09.2026')).toBe(true);
        expect(passtZurRechnungsSuche(vorschlag, 'zzz')).toBe(false);
    });
});

describe('API', () => {
    it('lädt Vorschläge mit kodierten Parametern', async () => {
        const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => [vorschlag] });
        vi.stubGlobal('fetch', fetchMock);
        expect(await ladeRechnungsVorschlaege([1, 2])).toEqual([vorschlag]);
        expect(fetchMock).toHaveBeenCalledWith('/api/bestellungen-uebersicht/rechnung-vorschlaege?dokumentIds=1&dokumentIds=2');
    });
    it('liefert bei unerwarteter Antwort eine leere Liste', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({}) }));
        expect(await ladeRechnungsVorschlaege([1])).toEqual([]);
    });
    it('wirft mit Meldung des Backends oder Standardtext', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, json: async () => ({ message: 'Ungültige IDs' }) }));
        await expect(ladeRechnungsVorschlaege([1])).rejects.toThrow('Ungültige IDs');
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, json: async () => { throw new Error('leer'); } }));
        await expect(ladeRechnungsVorschlaege([1])).rejects.toThrow('Rechnungen konnten nicht geladen werden.');
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, json: async () => ({ message: '  ' }) }));
        await expect(rechnungVerknuepfen(1, 2)).rejects.toThrow('Rechnung konnte nicht zugeordnet werden.');
    });
    it('verknüpft per POST', async () => {
        const fetchMock = vi.fn().mockResolvedValue({ ok: true });
        vi.stubGlobal('fetch', fetchMock);
        await rechnungVerknuepfen(3, 7);
        const [url, init] = fetchMock.mock.calls[0];
        expect(url).toBe('/api/bestellungen-uebersicht/rechnung-verknuepfen');
        expect(init.method).toBe('POST');
        expect(JSON.parse(init.body)).toEqual({ bestellDokumentId: 3, rechnungDokumentId: 7 });
    });
});
