import { afterEach, describe, expect, it, vi } from 'vitest';
import type { HiCadZeile, PositionSnapshot } from './types';
import type { GruppenEntscheidung, SaegelisteZeile } from './originalHicadApi';

const snapshot = (werte: Partial<PositionSnapshot> = {}): PositionSnapshot => ({
    art: 'ZEICHNUNGSTEIL', artikelId: null, interneReferenz: '1101', zeichnungsnummer: 'Z-4711', zeichnungsrevision: 'Ungeprüft',
    bezeichnung: 'Stütze', werkstoff: 'S235JRH', abmessung: 'Rohr 76.1x4',
    basis: { menge: 2, einheit: 'STUECK', stueckzahl: 2, einzelLaengeMm: 2835.7, kgJeMeter: 7.111053, faktorQuelle: 'HiCAD-Stückgewicht' },
    schnittForm: 'Anschnitt Steg', winkelLinks: null, winkelRechts: '0°', bearbeitung: null, oberflaeche: 'verzinkt',
    dokumente: [], anlageVersionIds: [], ...werte,
});
const zeile = (zeilennummer: number, vorschlag: PositionSnapshot | null, werte: Partial<HiCadZeile> = {}): HiCadZeile => ({
    zeilennummer, rohtext: 'Dummy', vorschlag, artikelKandidaten: [], bereitsUebernommen: false, hinweise: [], bilder: [], ...werte,
});
const vorschau = {
    id: 12, dateiHash: 'hash', dateiSchonImportiert: false,
    kopf: { zeichnungsnummer: 'Z-4711', auftragsnummer: 'A-0001', auftragstext: 'Dummy-Treppe', kunde: 'Max Mustermann' },
    zeilen: [
        zeile(9, snapshot({ interneReferenz: '1100', bezeichnung: 'Rohr 76.1x4', schnittForm: null, winkelRechts: null,
            basis: { menge: 1, einheit: 'STUECK', stueckzahl: null, einzelLaengeMm: 763.6, kgJeMeter: 7.111053, faktorQuelle: null } })),
        zeile(10, snapshot(), { bilder: [{ dateiId: 88, dateiname: 'anschnitt.png', mimeTyp: 'image/png', byteAnzahl: 10, url: '/api/einkauf/hicad/12/bilder/88' }] }),
        zeile(11, snapshot({ interneReferenz: '1200', bezeichnung: 'HEB 220', abmessung: 'HEB 220', werkstoff: 'S235JR', schnittForm: 'Anschnitt Flansch',
            winkelLinks: '45°', winkelRechts: '45°', basis: { menge: 1, einheit: 'STUECK', stueckzahl: 1, einzelLaengeMm: 5076.5, kgJeMeter: null, faktorQuelle: null } })),
        zeile(12, null, { hinweise: ['Menge muss größer als 0 sein.'] }),
    ],
};
const fortschritt = { id: 12, version: 3, duplikat: false, zeilen: [] };
const ok = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });
const adapter = async (modus = 'connected') => { vi.stubEnv('VITE_EN1090_API_MODE', modus); vi.resetModules(); return import('./originalHicadApi'); };
const entscheidung = (werte: Partial<GruppenEntscheidung> = {}): GruppenEntscheidung => ({
    aggregieren: false, artikelId: null, artikelProduktname: null, lieferantId: null, lieferantName: null, stangenlaengeM: null, ...werte,
});
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs(); });

describe('Zuschnitt-Optimierung (FFD)', () => {
    it('packt Zuschnitte absteigend in möglichst wenige Stangen und zählt Überlängen', async () => {
        const { optimiereStangen } = await adapter();
        const zeilen: SaegelisteZeile[] = [{ anzahl: 3, bezeichnung: 'RR 40x40', laengeMm: 2500 }, { anzahl: 2, bezeichnung: 'RR 40x40', laengeMm: 1000 }];
        expect(optimiereStangen(zeilen, 6)).toEqual({ anzahlStangen: 2, belegtMm: 9500, verschnittMm: 2500, ueberlange: 0 });
        expect(optimiereStangen([{ anzahl: 1, bezeichnung: 'HEB 220', laengeMm: 7000 }], 6)).toMatchObject({ anzahlStangen: 1, ueberlange: 1 });
        expect(optimiereStangen([{ anzahl: 2, bezeichnung: 'x', laengeMm: 3000.05 }], 6).anzahlStangen).toBe(2);
        expect(optimiereStangen([{ anzahl: 0, bezeichnung: 'x', laengeMm: 3000 }, { anzahl: 2, bezeichnung: 'x' }], 6).anzahlStangen).toBe(0);
    });
});

describe('Anschnittwinkel 0° (gerader Schnitt)', () => {
    const bild = { dateiId: 88, dateiname: 'anschnitt.png', mimeTyp: 'image/png', byteAnzahl: 10, url: '/api/einkauf/hicad/12/bilder/88' };

    it('behandelt 0, 0°, 0,0° und 0.0° wie keinen Winkel', async () => {
        const { echterWinkel } = await adapter();
        for (const nullGrad of ['0', '0°', '0,0°', '0.0°', ' 0 ° ', '', null, undefined]) expect(echterWinkel(nullGrad)).toBeNull();
        expect(echterWinkel('45°')).toBe('45°');
        expect(echterWinkel('22,5°')).toBe('22,5°');
        expect(echterWinkel('0,5°')).toBe('0,5°');
    });

    it('zeigt eine Zeile mit 0°/0° wie einen normalen Fixzuschnitt ohne Winkel und ohne Anschnittbild', async () => {
        const { saegelisteZeile } = await adapter();
        const z = saegelisteZeile(zeile(20, snapshot({ winkelLinks: '0°', winkelRechts: '0,0°' }), { bilder: [bild] }))!;
        expect(z).toMatchObject({ anzahl: 2, laengeMm: 2835.7, anschnittSteg: undefined, anschnittFlansch: undefined,
            anschnittbildStegUrl: null, anschnittbildFlanschUrl: null, bildDateiIds: [88] });
    });

    it('zeigt bei 45°/0° nur die 45°-Seite', async () => {
        const { saegelisteZeile } = await adapter();
        expect(saegelisteZeile(zeile(21, snapshot({ winkelLinks: '45°', winkelRechts: '0' }), { bilder: [bild] })))
            .toMatchObject({ anschnittSteg: '45°', anschnittbildStegUrl: '/api/einkauf/hicad/12/bilder/88' });
        expect(saegelisteZeile(zeile(22, snapshot({ schnittForm: 'Anschnitt Flansch', winkelLinks: '0.0°', winkelRechts: '45°' }))))
            .toMatchObject({ anschnittFlansch: '45°', anschnittSteg: undefined });
    });
});

describe('HiCAD-Vorschau mit Datenbank-API', () => {
    it('übersetzt Importzeilen in Profilgruppen mit Kopfdaten, Schnittbildern, Winkeln und Stammartikel', async () => {
        const fetcher = vi.fn(async (url: string) => {
            if (url.startsWith('/api/einkauf/hicad/vorschau?projektId=7')) return ok(vorschau, 201);
            if (url === '/api/einkauf/hicad/12') return ok(fortschritt);
            if (url.startsWith('/api/artikel?') && url.includes('HEB')) return ok({ artikel: [
                { id: 5, produktname: 'HEB 220', werkstoffName: 'S355J2', verpackungseinheit: 12 },
                { id: 6, produktname: 'HEB220', werkstoffName: 'S235JR', verpackungseinheit: 12 }] });
            if (url.startsWith('/api/artikel?')) return new Response('{}', { status: 500 });
            throw new Error(`Unerwartete API ${url}`);
        });
        vi.stubGlobal('fetch', fetcher);
        const { ladeHicadVorschau } = await adapter();
        const preview = await ladeHicadVorschau(new File(['x'], 'saegeliste.xlsx'), 7);

        expect(preview).toMatchObject({ importId: 12, importVersion: 3, auftragsnummer: 'A-0001', auftragstext: 'Dummy-Treppe', kunde: 'Max Mustermann', zeichnungsnr: 'Z-4711' });
        expect(preview.unlesbareZeilen).toEqual([{ zeilennummer: 12, grund: 'Menge muss größer als 0 sein.' }]);
        expect(preview.gruppen.map(g => g.bezeichnung)).toEqual(['Rohr 76.1x4', 'HEB 220']);
        const rohr = preview.gruppen[0];
        expect(rohr).toMatchObject({ werkstoff: 'S235JRH', summeStueck: 3, summeMeter: 6.44, berechneteStaebe: 2, artikelId: null });
        // 0° ist ein gerader Schnitt: keine Winkelanzeige, kein Anschnittbild – die Bild-ID bleibt für die Übernahme.
        expect(rohr.zeilen[1]).toMatchObject({ posNr: '1101', anzahl: 2, laengeMm: 2835.7, anschnittSteg: undefined,
            anschnittbildStegUrl: null, anschnittbildFlanschUrl: null, bildDateiIds: [88], benennung: 'Stütze' });
        const traeger = preview.gruppen[1];
        expect(traeger).toMatchObject({ artikelId: 6, artikelProduktname: 'HEB220', verpackungseinheitM: 12 });
        expect(traeger.zeilen[0]).toMatchObject({ anschnittFlansch: '45° 45°', anschnittSteg: undefined });
    });

    it('meldet die konkrete Servermeldung, wenn die Sägeliste nicht gelesen werden kann', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => ok({ message: 'Makros und externe Verknüpfungen sind nicht erlaubt.' }, 400)));
        const { ladeHicadVorschau } = await adapter();
        await expect(ladeHicadVorschau(new File(['x'], 'saegeliste.xlsx'), 7)).rejects.toThrow('Makros und externe Verknüpfungen sind nicht erlaubt.');
    });

    it('meldet eine Datei ohne lesbare Zeile verständlich statt eine leere Vorschau zu zeigen', async () => {
        vi.stubGlobal('fetch', vi.fn(async (url: string) => url.includes('vorschau')
            ? ok({ ...vorschau, zeilen: [zeile(12, null, { hinweise: ['Menge muss größer als 0 sein.'] })] }, 201) : ok(fortschritt)));
        const { ladeHicadVorschau } = await adapter();
        await expect(ladeHicadVorschau(new File(['x'], 'saegeliste.xlsx'), 7)).rejects.toThrow('Zeile 12: Menge muss größer als 0 sein.');
    });

    it('nutzt im Mock-Modus weiterhin die Endpunkte des EN1090-Fensters', async () => {
        const fetcher = vi.fn(async () => new Response('', { status: 400, headers: { 'X-Error-Reason': 'Kein Sheet Sägeliste' } }));
        vi.stubGlobal('fetch', fetcher);
        const { ladeHicadVorschau } = await adapter('mock');
        await expect(ladeHicadVorschau(new File(['x'], 'saegeliste.xlsx'), 7)).rejects.toThrow('Kein Sheet Sägeliste');
        expect(fetcher).toHaveBeenCalledWith('/api/bestellungen/import/hicad/preview', expect.objectContaining({ method: 'POST' }));
    });
});

describe('HiCAD-Übernahme in den Projektbedarf', () => {
    const ladeVorschau = async () => {
        vi.stubGlobal('fetch', vi.fn(async (url: string) => url.includes('vorschau') ? ok(vorschau, 201)
            : url === '/api/einkauf/hicad/12' ? ok(fortschritt) : ok({ artikel: [] })));
        const api = await adapter();
        return { api, preview: await api.ladeHicadVorschau(new File(['x'], 'saegeliste.xlsx'), 7) };
    };

    it('legt Fixzuschnitte über die Importübernahme und Stangenware als Meterbedarf im Projekt an', async () => {
        const { api, preview } = await ladeVorschau();
        const [rohr, traeger] = preview.gruppen;
        const fetcher = vi.fn(async (url: string) => url.endsWith('/uebernehmen') ? ok([{ id: 1 }, { id: 2 }]) : ok({ id: 3 }));
        vi.stubGlobal('fetch', fetcher);
        const ergebnis = await api.uebernehmeHicad({ preview, projektId: 7, idempotenzKey: 'key-1', entscheidungen: {
            [rohr.groupKey]: entscheidung({ lieferantId: 4, lieferantName: 'Test-Stahlhandel' }),
            [traeger.groupKey]: entscheidung({ aggregieren: true, artikelId: 6, stangenlaengeM: 12 }),
        } });

        expect(ergebnis).toEqual({ angelegtePositionen: 3, erledigteGruppen: [rohr.groupKey, traeger.groupKey] });
        const [uebernahmeUrl, uebernahmeInit] = fetcher.mock.calls[0] as unknown as [string, RequestInit];
        expect(uebernahmeUrl).toBe('/api/einkauf/hicad/12/uebernehmen');
        const uebernahme = JSON.parse(uebernahmeInit.body as string);
        expect(uebernahme).toMatchObject({ version: 3, duplikatBewusst: false, idempotenzKey: 'key-1' });
        expect(uebernahme.zeilen).toHaveLength(2);
        expect(uebernahme.zeilen[1]).toMatchObject({ zeilennummer: 10, menge: 2, bestaetigteBildDateiIds: [88], korrigiert: {
            art: 'FREITEXT', artikelId: null, bezeichnung: 'Rohr 76.1x4', werkstoff: 'S235JRH', abmessung: 'Rohr 76.1x4',
            winkelRechts: '0°', schnittForm: 'Anschnitt Steg', zeichnungsrevision: null, bearbeitung: 'HiCAD A-0001 · Pos 1101 · Stütze',
            basis: { menge: 2, einheit: 'STUECK', einzelLaengeMm: 2835.7 }, beschaffungsdetails: { lieferantId: 4 } } });

        const [bedarfUrl, bedarfInit] = fetcher.mock.calls[1] as unknown as [string, RequestInit];
        expect(bedarfUrl).toBe('/api/einkauf/bedarf');
        const bedarf = JSON.parse(bedarfInit.body as string);
        expect(bedarf.liefergruppe).toEqual({ lieferadresse: null, bedarfstermin: null, projektId: 7, lagerzweck: null });
        expect(bedarf.position).toMatchObject({ art: 'ARTIKEL', artikelId: 6, bezeichnung: 'HEB 220', werkstoff: 'S235JR', zeichnungsnummer: 'Z-4711',
            basis: { menge: 12, einheit: 'METER', stueckzahl: 1, einzelLaengeMm: 12000 } });
        expect(bedarf.position.bearbeitung).toContain('Stangenware · 1 Stk à 12 m');
    });

    it('legt Gruppen mit Artikel und Freitext-Gruppen ohne Lieferant an', async () => {
        const { api, preview } = await ladeVorschau();
        const [rohr, traeger] = preview.gruppen;
        const fetcher = vi.fn(async () => ok([{ id: 1 }, { id: 2 }, { id: 3 }]));
        vi.stubGlobal('fetch', fetcher);
        const ergebnis = await api.uebernehmeHicad({ preview, projektId: 7, idempotenzKey: 'k', entscheidungen: {
            [rohr.groupKey]: entscheidung(),
            [traeger.groupKey]: entscheidung({ artikelId: 6, artikelProduktname: 'HEB 220', artikelnummer: 'ST-0815' }),
        } });

        expect(ergebnis.angelegtePositionen).toBe(3);
        expect(fetcher).toHaveBeenCalledTimes(1);
        const zeilen = JSON.parse((fetcher.mock.calls[0] as unknown as [string, RequestInit])[1].body as string).zeilen;
        expect(zeilen.map((z: { korrigiert: { art: string; artikelId: number | null; beschaffungsdetails: { lieferantId: number | null } } }) =>
            [z.korrigiert.art, z.korrigiert.artikelId, z.korrigiert.beschaffungsdetails.lieferantId]))
            .toEqual([['FREITEXT', null, null], ['FREITEXT', null, null], ['ARTIKEL', 6, null]]);
    });

    it('prüft alle Stangenlängen vor dem ersten Schreibzugriff', async () => {
        const { api, preview } = await ladeVorschau();
        const fetcher = vi.fn(async () => ok([])); vi.stubGlobal('fetch', fetcher);
        const [rohr, traeger] = preview.gruppen;
        await expect(api.uebernehmeHicad({ preview, projektId: 7, idempotenzKey: 'k', entscheidungen: {
            [rohr.groupKey]: entscheidung(), [traeger.groupKey]: entscheidung({ aggregieren: true, stangenlaengeM: 0 }),
        } })).rejects.toThrow('Stangenlänge');
        expect(fetcher).not.toHaveBeenCalled();
    });

    it('meldet Serverfehler der Übernahme mit deutscher Meldung und legt danach nichts weiter an', async () => {
        const { api, preview } = await ladeVorschau();
        const fetcher = vi.fn(async () => ok({ message: 'Die Importvorschau wurde zwischenzeitlich geändert.' }, 409));
        vi.stubGlobal('fetch', fetcher);
        const entscheidungen = Object.fromEntries(preview.gruppen.map(g => [g.groupKey, entscheidung()]));
        await expect(api.uebernehmeHicad({ preview, projektId: 7, idempotenzKey: 'k', entscheidungen }))
            .rejects.toThrow('Die Importvorschau wurde zwischenzeitlich geändert.');
        expect(fetcher).toHaveBeenCalledTimes(1);
    });

    it('merkt sich bei einem Teilfehler die angelegten Gruppen und überspringt sie beim Wiederholen', async () => {
        const { api, preview } = await ladeVorschau();
        const [rohr, traeger] = preview.gruppen;
        const entscheidungen = { [rohr.groupKey]: entscheidung(), [traeger.groupKey]: entscheidung({ aggregieren: true, stangenlaengeM: 6 }) };
        vi.stubGlobal('fetch', vi.fn(async (url: string) => url.endsWith('/uebernehmen') ? ok([{ id: 1 }, { id: 2 }])
            : ok({ message: 'Der ausgewählte Lieferant wurde nicht gefunden.' }, 404)));
        const fehler = await api.uebernehmeHicad({ preview, projektId: 7, idempotenzKey: 'k', entscheidungen }).catch(e => e);
        expect(fehler).toBeInstanceOf(api.HicadUebernahmeFehler);
        expect(fehler.message).toContain('„HEB 220“ konnte nicht angelegt werden: Der ausgewählte Lieferant wurde nicht gefunden.');
        expect(fehler).toMatchObject({ angelegtePositionen: 2, erledigteGruppen: [rohr.groupKey] });

        const fetcher = vi.fn(async () => ok({ id: 3 })); vi.stubGlobal('fetch', fetcher);
        const zweiterVersuch = await api.uebernehmeHicad({ preview, projektId: 7, idempotenzKey: 'k', entscheidungen, erledigteGruppen: fehler.erledigteGruppen });
        expect(zweiterVersuch.angelegtePositionen).toBe(1);
        expect(fetcher).toHaveBeenCalledTimes(1);
        expect(fetcher.mock.calls[0][0]).toBe('/api/einkauf/bedarf');
    });

    it('sendet bei einer schon importierten Datei die bewusste Bestätigung mit', async () => {
        const { api, preview } = await ladeVorschau();
        const fetcher = vi.fn(async () => ok([])); vi.stubGlobal('fetch', fetcher);
        const entscheidungen = Object.fromEntries(preview.gruppen.map(g => [g.groupKey, entscheidung()]));
        await api.uebernehmeHicad({ preview: { ...preview, dateiSchonImportiert: true }, projektId: 7, idempotenzKey: 'k', entscheidungen, duplikatBewusst: true });
        expect(JSON.parse((fetcher.mock.calls[0] as unknown as [string, RequestInit])[1].body as string).duplikatBewusst).toBe(true);
    });
});
