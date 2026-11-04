import { describe, it, expect, vi, afterEach } from 'vitest';
import type { AusgangsGeschaeftsDokument } from '../../types';
import { ladeBezugKontext, ladeStammdatenKontext } from './dokumentKontextLaden';

function stubbeFetch(routen: Record<string, unknown>) {
    const fetchMock = vi.fn((url: string) => {
        if (!(url in routen)) return Promise.resolve({ ok: false, status: 404, json: () => Promise.resolve(null) });
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(routen[url]) });
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

const dokument = (abweichung: Partial<AusgangsGeschaeftsDokument>) => ({
    id: 1, typ: 'RECHNUNG', gebucht: false, ...abweichung,
}) as AusgangsGeschaeftsDokument;

const nie = () => false;

afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('ladeStammdatenKontext', () => {
    it('nimmt bei gebuchten oder nicht verknüpften Dokumenten nur die eigenen Werte, ohne Anfrage', async () => {
        const fetchMock = stubbeFetch({});
        const gebucht = await ladeStammdatenKontext(dokument({ gebucht: true, kundeId: 7, kundenName: 'Max Mustermann' }), nie);
        const lose = await ladeStammdatenKontext(dokument({ kundenName: 'Max Mustermann' }), nie);

        expect(gebucht?.({})).toMatchObject({ kundenName: 'Max Mustermann', zahlungsziel: 8 });
        expect(lose?.({})).toMatchObject({ kundenName: 'Max Mustermann' });
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('lädt Projekt, Anfrage oder Kunde je nach Verknüpfung', async () => {
        stubbeFetch({
            '/api/projekte/3': { auftragsnummer: 'P-1', kunde: 'Max Mustermann', kundeDto: { anrede: 'HERR' } },
            '/api/anfragen/4': { kundenName: 'Erika Musterfrau', kundenAnrede: 'FRAU', anfragesnummer: 'AF-1' },
            '/api/kunden/7': { name: 'Landkreis Musterstadt', anrede: 'DAMEN_HERREN' },
        });

        expect((await ladeStammdatenKontext(dokument({ projektId: 3 }), nie))?.({}))
            .toMatchObject({ projektnummer: 'P-1', anrede: 'HERR' });
        expect((await ladeStammdatenKontext(dokument({ anfrageId: 4 }), nie))?.({}))
            .toMatchObject({ kundenName: 'Erika Musterfrau', anrede: 'FRAU', bezugsdokument: 'AF-1' });
        expect((await ladeStammdatenKontext(dokument({ kundeId: 7 }), nie))?.({}))
            .toMatchObject({ kundenName: 'Landkreis Musterstadt', anrede: 'DAMEN_HERREN', kundeId: 7 });
    });

    it('fragt beim Kunden erst beim Anwenden, ob die Adresse bleiben soll', async () => {
        stubbeFetch({ '/api/kunden/7': { name: 'Max Mustermann' } });
        let behalten = false;
        const aendern = await ladeStammdatenKontext(dokument({ kundeId: 7 }), () => behalten);
        behalten = true;
        expect(aendern?.({ rechnungsadresse: 'Selbst getippt' }).rechnungsadresse).toBe('Selbst getippt');
    });

    it('ändert nichts, wenn der Server ablehnt, und gibt Netzwerkfehler weiter', async () => {
        stubbeFetch({});
        expect(await ladeStammdatenKontext(dokument({ projektId: 3 }), nie)).toBeNull();

        vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('offline'))));
        await expect(ladeStammdatenKontext(dokument({ kundeId: 7 }), nie)).rejects.toThrow('offline');
    });
});

describe('ladeBezugKontext', () => {
    it('übernimmt Nummer, Typ und Datum des Vorgängers', async () => {
        stubbeFetch({ '/api/ausgangs-dokumente/9': { typ: 'AUFTRAGSBESTAETIGUNG', dokumentNummer: 'AB-1', datum: '2026-10-01' } });
        const aendern = await ladeBezugKontext(dokument({ vorgaengerId: 9 }));
        expect(aendern?.({ kundenName: 'Max Mustermann' })).toEqual({
            kundenName: 'Max Mustermann',
            bezugsdokument: 'AB-1',
            bezugsdokumentTyp: 'Auftragsbestätigung',
            bezugsdokumentDatum: '01.10.2026',
        });
    });

    it('verschluckt Fehler beim Vorgänger und meldet sie in der Konsole', async () => {
        vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('offline'))));
        const fehler = vi.spyOn(console, 'error').mockImplementation(() => {});
        expect(await ladeBezugKontext(dokument({ vorgaengerId: 9 }))).toBeNull();
        expect(fehler).toHaveBeenCalled();
    });

    it('setzt bei Folgedokumenten einer Anfrage den Bezugstyp „Angebot“, sonst nichts', async () => {
        const fetchMock = stubbeFetch({});
        expect((await ladeBezugKontext(dokument({ anfrageId: 4 })))?.({})).toEqual({ bezugsdokumentTyp: 'Angebot' });
        expect(await ladeBezugKontext(dokument({ anfrageId: 4, typ: 'ANGEBOT' }))).toBeNull();
        expect(await ladeBezugKontext(dokument({}))).toBeNull();
        expect(fetchMock).not.toHaveBeenCalled();
    });
});
