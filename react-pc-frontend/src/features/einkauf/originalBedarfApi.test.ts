import { afterEach, describe, expect, it, vi } from 'vitest';
import type { BedarfResponse } from './types';

const bedarf = (id: number, projektId: number | null = 7): BedarfResponse => ({
    id, version: 3,
    position: { art: 'FREITEXT', artikelId: null, interneReferenz: null, zeichnungsnummer: null, zeichnungsrevision: null,
        bezeichnung: 'Testmaterial', werkstoff: 'Stahl', abmessung: '20 × 20',
        basis: { menge: 12.5, einheit: 'METER', stueckzahl: null, einzelLaengeMm: null, kgJeMeter: 2, faktorQuelle: 'Test' },
        schnittForm: null, winkelLinks: null, winkelRechts: null, bearbeitung: 'Testnotiz', oberflaeche: 'verzinkt', dokumente: [], anlageVersionIds: [9] },
    liefergruppe: { projektId, lagerzweck: null, lieferadresse: null, bedarfstermin: null },
    mengen: { bedarf: 12.5, lagergedeckt: 1.5, angefragt: 0, reserviert: 0, bestellt: 0, geliefert: 0, storniert: 0, ungedeckt: 11, disponierbar: 11 },
    nachpflegeErforderlich: false, historischerHinweis: null,
});
const ok = (data: unknown) => new Response(JSON.stringify(data), { headers: { 'Content-Type': 'application/json' } });
const adapter = async () => { vi.stubEnv('VITE_EN1090_API_MODE', 'connected'); vi.resetModules(); return import('./originalBedarfApi'); };
afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs(); });

describe('Original-Bedarf mit Datenbank-API', () => {
    it('lädt alle Seiten und erhält Snapshot, Version, Dezimalmengen und Projektbezeichnung', async () => {
        const fetcher = vi.fn(async (url: string) => {
            if (url.startsWith('/api/projekte/simple')) return ok([{ id: 7, bauvorhaben: 'Testprojekt', kunde: 'Max Mustermann' }]);
            if (url.includes('page=0')) return ok({ content: [bedarf(1)], totalPages: 2 });
            if (url.includes('page=1')) return ok({ content: [bedarf(2)], totalPages: 2 });
            throw new Error('Unerwartete API');
        });
        vi.stubGlobal('fetch', fetcher);
        const { ladeBedarfszeilen } = await adapter();
        const result = await ladeBedarfszeilen(7);
        expect(result).toHaveLength(2);
        expect(result[0]).toMatchObject({ menge: 12.5, einheit: 'm', kilogramm: 25, vorhanden: 1.5, bestellen: 11, version: 3, projektName: 'Testprojekt' });
        expect(result[0].bedarf.position.anlageVersionIds).toEqual([9]);
        expect(fetcher.mock.calls.filter(([url]) => url.includes('/bedarf?')).every(([url]) => url.includes('projektId=7'))).toBe(true);
    });

    it('sucht Vorrat ohne Projekt und löst Projekt oberhalb der 500er-Liste gesondert auf', async () => {
        const fetcher = vi.fn(async (url: string) => url.startsWith('/api/projekte/simple') ? ok([])
            : url === '/api/projekte/7' ? ok({ id: 7, bauvorhaben: 'Weiteres Testprojekt' })
            : ok({ content: [bedarf(1)], totalPages: 1 }));
        vi.stubGlobal('fetch', fetcher);
        const { ladeBedarfszeilen } = await adapter();
        expect((await ladeBedarfszeilen())[0].projektName).toBe('Weiteres Testprojekt');
        await ladeBedarfszeilen(null);
        expect(fetcher.mock.calls.some(([url]) => url.includes('ohneProjekt=true'))).toBe(true);
    });

    it('speichert ausschließlich veränderte Werkstattmengen mit der gelesenen Version', async () => {
        const fetcher = vi.fn(async () => ok([])); vi.stubGlobal('fetch', fetcher);
        const { speichereWerkstatt } = await adapter();
        await speichereWerkstatt([{ bedarf: bedarf(1) }, { bedarf: bedarf(2) }], { 1: 2.5, 2: 1.5 });
        expect(fetcher).toHaveBeenCalledWith('/api/einkauf/bedarf/werkstattpruefung', expect.objectContaining({
            method: 'PUT', body: JSON.stringify({ positionen: [{ bedarfId: 1, version: 3, vorhanden: 2.5 }] }),
        }));
    });

    it('prüft alle Mengen vor dem ersten Schreibzugriff und meldet Versionskonflikte', async () => {
        const fetcher = vi.fn(async () => new Response(JSON.stringify({ message: 'Zwischenzeitlich geändert.' }), { status: 409 }));
        vi.stubGlobal('fetch', fetcher);
        const { speichereWerkstatt } = await adapter();
        await expect(speichereWerkstatt([{ bedarf: bedarf(1) }, { bedarf: bedarf(2) }], { 1: 2.5, 2: 20 })).rejects.toThrow();
        expect(fetcher).not.toHaveBeenCalled();
        await expect(speichereWerkstatt([{ bedarf: bedarf(1) }], { 1: 2.5 })).rejects.toThrow('Zwischenzeitlich geändert.');
    });

    it('liefert die vollständige Liste, auch wenn ein Projekt oder Lieferant nicht mehr gefunden wird', async () => {
        const mitLieferant = (id: number, projektId: number, lieferantId: number): BedarfResponse => {
            const b = bedarf(id, projektId);
            return { ...b, position: { ...b.position, beschaffungsdetails: { lieferantId } } as BedarfResponse['position'] };
        };
        const fetcher = vi.fn(async (url: string) => {
            if (url.startsWith('/api/projekte/simple')) return ok([]);
            if (url === '/api/projekte/7') return ok({ id: 7, bauvorhaben: 'Testprojekt' });
            if (url === '/api/projekte/8') return new Response('{}', { status: 404 });
            if (url === '/api/lieferanten/3') return ok({ lieferantenname: 'Test-Stahlhandel' });
            if (url === '/api/lieferanten/4') return new Response('{}', { status: 404 });
            return ok({ content: [mitLieferant(1, 7, 3), mitLieferant(2, 8, 4)], totalPages: 1 });
        });
        vi.stubGlobal('fetch', fetcher);
        const { ladeBedarfszeilen } = await adapter();
        const result = await ladeBedarfszeilen();
        expect(result).toHaveLength(2);
        expect(result[0]).toMatchObject({ id: 1, projektName: 'Testprojekt', lieferantName: 'Test-Stahlhandel' });
        expect(result[1]).toMatchObject({ id: 2, projektId: 8, projektName: undefined, lieferantId: 4, lieferantName: undefined });
    });

    it('zeigt als Lieferanten-Artikelnummer nie die interne Referenz an', async () => {
        const { bedarfszeile } = await adapter();
        const b = bedarf(1);
        const ohneDetails = { ...b, position: { ...b.position, art: 'ARTIKEL', interneReferenz: 'INT-4711' } } as BedarfResponse;
        expect(bedarfszeile(ohneDetails).externeArtikelnummer).toBeUndefined();
        const mitDetails = { ...ohneDetails, position: { ...ohneDetails.position,
            beschaffungsdetails: { externeArtikelnummer: 'LIEF-0815' } } } as BedarfResponse;
        expect(bedarfszeile(mitDetails).externeArtikelnummer).toBe('LIEF-0815');
    });

    it('übernimmt HiCAD-Positionsnummer, Mantelfläche und das Positionsgewicht auch bei Stückpositionen', async () => {
        const { bedarfszeile } = await adapter();
        const b = bedarf(1);
        const hicad = { ...b, position: { ...b.position, art: 'ARTIKEL', artikelId: 6, positionsnummer: '1200',
            basis: { menge: 1, einheit: 'STUECK', stueckzahl: 1, einzelLaengeMm: 5076.5, kgJeMeter: null, faktorQuelle: null,
                gesamtgewichtKg: 347.381, mantelflaecheM2: 6.3483 } } } as BedarfResponse;
        expect(bedarfszeile(hicad)).toMatchObject({ positionsnummer: '1200', mantelflaecheM2: 6.3483, kilogramm: 347.381, einheit: 'Stück' });
        expect(bedarfszeile(b)).toMatchObject({ positionsnummer: null, mantelflaecheM2: null, kilogramm: 25 });
    });

    it('verschleiert fehlgeschlagene Backendantworten nicht als leere Bedarfsliste', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => new Response('{}', { status: 503 })));
        const { ladeBedarfszeilen } = await adapter();
        await expect(ladeBedarfszeilen()).rejects.toThrow('503');
    });
});

describe('Bedarf löschen', () => {
    it('schickt DELETE mit Version und ohne Body', async () => {
        const fetcher = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(async () => new Response(null, { status: 204 }));
        vi.stubGlobal('fetch', fetcher);
        const { loescheBedarf } = await adapter();
        await loescheBedarf({ id: 5, version: 3 });
        expect(fetcher).toHaveBeenCalledWith('/api/einkauf/bedarf/5?version=3', { method: 'DELETE' });
    });

    it('reicht die Servermeldung bei 409 weiter', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
            message: 'Der Bedarf ist bereits in Preisanfrage PA-2026-0001 enthalten und kann nicht gelöscht werden.', fieldErrors: [],
        }), { status: 409 })));
        const { loescheBedarf } = await adapter();
        await expect(loescheBedarf({ id: 5, version: 3 })).rejects.toThrow('Preisanfrage PA-2026-0001');
    });

    it('nennt den Grund, wenn der Bedarf schon weiterverarbeitet ist', async () => {
        const { loeschSperrgrund } = await adapter();
        const frei = { ...bedarf(1), mengen: { ...bedarf(1).mengen, lagergedeckt: 0 } };
        expect(loeschSperrgrund(frei)).toBeNull();
        expect(loeschSperrgrund(undefined)).toBeNull();
        expect(loeschSperrgrund(bedarf(1))).toContain('„Vorhanden“ auf 0');
        expect(loeschSperrgrund({ ...frei, mengen: { ...frei.mengen, angefragt: 2 } })).toContain('Preisanfrage');
        expect(loeschSperrgrund({ ...frei, mengen: { ...frei.mengen, reserviert: 1 } })).toContain('reserviert');
        expect(loeschSperrgrund({ ...frei, mengen: { ...frei.mengen, bestellt: 1 } })).toContain('bestellt');
        expect(loeschSperrgrund({ ...frei, mengen: { ...frei.mengen, geliefert: 1 } })).toContain('bestellt');
    });
});
