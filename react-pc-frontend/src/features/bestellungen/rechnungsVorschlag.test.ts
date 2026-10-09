import { afterEach, describe, expect, it, vi } from 'vitest';
import {
    RECHNUNGS_DATEI_ACCEPT,
    brauchtRueckfrage,
    dokumentAbhaengen,
    istErlaubteRechnungsDatei,
    rechnungHochladen,
    rueckfrage,
    antwortFehlerText,
    formatiereQuote,
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

describe('rueckfrage', () => {
    it('fragt bei schon zugeordneter Rechnung nach Teillieferung', () => {
        const frage = rueckfrage({ ...vorschlag, gehoertSchonZu: 'Lieferschein LS-4711' });
        expect(frage?.title).toBe('Rechnung gehört schon zu einer Bestellung');
        expect(frage?.message).toContain('gehört schon zu Lieferschein LS-4711');
        expect(frage?.message).toContain('Teillieferung');
        expect(frage?.confirmLabel).toBe('Zusammenfassen');
    });
    it('fragt bei unsicherem Vorschlag nach, sonst nicht', () => {
        expect(rueckfrage(vorschlag)).toBeNull();
        expect(rueckfrage({ ...vorschlag, gehoertSchonZu: null })).toBeNull();
        expect(rueckfrage({ ...vorschlag, trefferquote: 20 })?.title).toBe('Rechnung wirklich zuordnen?');
    });
});

describe('istErlaubteRechnungsDatei', () => {
    it('lässt nur PDF, JPG und PNG zu', () => {
        expect(istErlaubteRechnungsDatei({ name: 'Rechnung.PDF', type: 'application/pdf' })).toBe(true);
        expect(istErlaubteRechnungsDatei({ name: 'foto.jpeg', type: 'image/jpeg' })).toBe(true);
        expect(istErlaubteRechnungsDatei({ name: 'scan.png', type: '' })).toBe(true);
        expect(istErlaubteRechnungsDatei({ name: 'virus.exe', type: 'application/octet-stream' })).toBe(false);
        expect(istErlaubteRechnungsDatei({ name: 'falsch.pdf', type: 'text/html' })).toBe(false);
        expect(RECHNUNGS_DATEI_ACCEPT).toContain('.pdf');
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

describe('antwortFehlerText', () => {
    it('nimmt message, dann error, sonst den Standardtext', async () => {
        const antwort = (body: unknown) => ({ json: async () => body }) as Response;
        expect(await antwortFehlerText(antwort({ message: 'Kaputt' }), 'Standard')).toBe('Kaputt');
        expect(await antwortFehlerText(antwort({ error: 'Datei zu groß' }), 'Standard')).toBe('Datei zu groß');
        expect(await antwortFehlerText(antwort({ message: ' ', error: '' }), 'Standard')).toBe('Standard');
        expect(await antwortFehlerText({ json: async () => { throw new Error('leer'); } } as unknown as Response, 'Standard')).toBe('Standard');
    });
});

describe('API', () => {
    it('nimmt auch eine Fehlermeldung im Feld „error“', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, json: async () => ({ error: 'Datei zu groß' }) }));
        await expect(rechnungVerknuepfen(1, 2)).rejects.toThrow('Datei zu groß');
    });
    it('hängt einen Beleg ab und liefert die Zahl gelöster Verknüpfungen', async () => {
        const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ geloest: 2 }) });
        vi.stubGlobal('fetch', fetchMock);
        expect(await dokumentAbhaengen(5)).toBe(2);
        const [url, init] = fetchMock.mock.calls[0];
        expect(url).toBe('/api/bestellungen-uebersicht/abhaengen');
        expect(init.method).toBe('POST');
        expect(JSON.parse(init.body)).toEqual({ dokumentId: 5 });

        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({}) }));
        expect(await dokumentAbhaengen(5)).toBe(0);
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => { throw new Error('leer'); } }));
        expect(await dokumentAbhaengen(5)).toBe(0);
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, json: async () => ({}) }));
        await expect(dokumentAbhaengen(5)).rejects.toThrow('Der Beleg konnte nicht abgehängt werden.');
    });
    it('lädt eine Rechnung als FormData hoch', async () => {
        const antwort = { ...vorschlag.rechnung, id: 99 };
        const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => antwort });
        vi.stubGlobal('fetch', fetchMock);
        const datei = new File(['%PDF'], 'rechnung.pdf', { type: 'application/pdf' });
        expect(await rechnungHochladen(12, datei)).toEqual(antwort);
        const [url, init] = fetchMock.mock.calls[0];
        expect(url).toBe('/api/bestellungen-uebersicht/rechnung-hochladen');
        expect(init.method).toBe('POST');
        const body = init.body as FormData;
        expect(body.get('bestellDokumentId')).toBe('12');
        expect((body.get('datei') as File).name).toBe('rechnung.pdf');

        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, json: async () => ({ message: 'Nur PDF' }) }));
        await expect(rechnungHochladen(12, datei)).rejects.toThrow('Nur PDF');
    });
    it('wirft beim Verknüpfen mit Standardtext, wenn der Server nichts sagt', async () => {
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
