import { renderHook, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi, afterEach } from 'vitest';
import type { AusgangsGeschaeftsDokument } from '../../types';
import { useDokumentKontext } from './useDokumentKontext';

function antwortenMit(routen: Record<string, unknown>) {
    return vi.fn((url: string) => {
        if (!(url in routen)) return Promise.resolve({ ok: false, status: 404, json: () => Promise.resolve(null) });
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(routen[url]) });
    });
}

const dokument = (abweichung: Partial<AusgangsGeschaeftsDokument>) => ({
    id: 1, typ: 'RECHNUNG', gebucht: false, ...abweichung,
}) as AusgangsGeschaeftsDokument;

afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('useDokumentKontext', () => {
    it('lädt bei einem neuen Dokument aus dem Projekt Kunde, Anrede und Betreff', async () => {
        vi.stubGlobal('fetch', antwortenMit({
            '/api/projekte/3': {
                auftragsnummer: 'P-1', bauvorhaben: 'Balkon', kunde: 'Landkreis Musterstadt',
                kundeDto: { anrede: 'DAMEN_HERREN', zahlungsziel: 30 },
            },
        }));
        const setzeBetreff = vi.fn();
        const { result } = renderHook(() => useDokumentKontext({ projektId: 3, dokumentTyp: 'ANGEBOT', setzeBetreff }));

        await waitFor(() => expect(result.current.kontextGeladen).toBe(true));
        expect(result.current.kontextDaten).toMatchObject({
            projektnummer: 'P-1', kundenName: 'Landkreis Musterstadt', anrede: 'DAMEN_HERREN', zahlungsziel: 30,
        });
        expect(setzeBetreff).toHaveBeenCalledWith('Balkon');
    });

    it('meldet auch ohne Projekt und Anfrage, dass der Kontext geladen ist', async () => {
        vi.stubGlobal('fetch', antwortenMit({}));
        const { result } = renderHook(() => useDokumentKontext({ dokumentTyp: 'ANGEBOT', setzeBetreff: vi.fn() }));
        await waitFor(() => expect(result.current.kontextGeladen).toBe(true));
        expect(result.current.kontextDaten).toEqual({});
    });

    it('lädt zu einem gespeicherten Dokument den Kunden und danach den Vorgänger', async () => {
        vi.stubGlobal('fetch', antwortenMit({
            '/api/kunden/7': { name: 'Max Mustermann', anrede: 'HERR', ansprechpartner: 'Mustermann' },
            '/api/ausgangs-dokumente/9': { typ: 'AUFTRAGSBESTAETIGUNG', dokumentNummer: 'AB-1', datum: '2026-10-01' },
        }));
        const { result } = renderHook(() => useDokumentKontext({ dokumentId: 1, dokumentTyp: 'RECHNUNG', setzeBetreff: vi.fn() }));

        await act(() => result.current.ladeFuerDokument(dokument({ kundeId: 7, vorgaengerId: 9 })));

        expect(result.current.kontextDaten).toMatchObject({
            kundenName: 'Max Mustermann', anrede: 'HERR', ansprechpartner: 'Mustermann', rechnungsadresse: 'Max Mustermann',
            bezugsdokument: 'AB-1', bezugsdokumentTyp: 'Auftragsbestätigung', bezugsdokumentDatum: '01.10.2026',
        });
        expect(result.current.adresseUserEditedRef.current).toBe(false);
    });

    it('zeigt bei einem Dokument nur am Kunden die gespeicherte Rechnungsadresse', async () => {
        const gespeichert = 'Max Mustermann\nAndere Str. 5\n12345 Musterstadt';
        vi.stubGlobal('fetch', antwortenMit({
            '/api/kunden/7': { name: 'Max Mustermann', strasse: 'Musterweg 1', plz: '12345', ort: 'Musterstadt' },
        }));
        const { result } = renderHook(() => useDokumentKontext({ dokumentId: 1, dokumentTyp: 'RECHNUNG', setzeBetreff: vi.fn() }));

        await act(() => result.current.ladeFuerDokument(dokument({ kundeId: 7, rechnungsadresseOverride: gespeichert })));

        expect(result.current.kontextDaten.rechnungsadresse).toBe(gespeichert);
        expect(result.current.kontextDatenRef.current.rechnungsadresse).toBe(gespeichert);
        expect(result.current.adresseUserEditedRef.current).toBe(true);
    });

    it.each([
        ['nur am Kunden', { kundeId: 7 }],
        ['am Projekt', { projektId: 3 }],
    ])('behält die gespeicherte Rechnungsadresse, wenn die Stammdaten (%s) nicht laden', async (_fall, bezug) => {
        vi.stubGlobal('fetch', antwortenMit({}));
        const { result } = renderHook(() => useDokumentKontext({ dokumentId: 1, dokumentTyp: 'RECHNUNG', setzeBetreff: vi.fn() }));

        await act(() => result.current.ladeFuerDokument(dokument({ ...bezug, rechnungsadresseOverride: 'Max Mustermann\nAndere Str. 5' })));

        expect(result.current.kontextDaten.rechnungsadresse).toBe('Max Mustermann\nAndere Str. 5');
        expect(result.current.kontextDatenRef.current.rechnungsadresse).toBe('Max Mustermann\nAndere Str. 5');
        expect(result.current.adresseUserEditedRef.current).toBe(true);
    });

    it('zeigt nach einem Dokumentwechsel die gespeicherte Adresse des neuen Dokuments', async () => {
        vi.stubGlobal('fetch', antwortenMit({ '/api/kunden/7': { name: 'Max Mustermann', strasse: 'Musterweg 1', plz: '12345', ort: 'Musterstadt' } }));
        const { result } = renderHook(() => useDokumentKontext({ dokumentId: 1, dokumentTyp: 'RECHNUNG', setzeBetreff: vi.fn() }));

        await act(() => result.current.ladeFuerDokument(dokument({ kundeId: 7, rechnungsadresseOverride: 'Adresse A' })));
        await act(() => result.current.ladeFuerDokument(dokument({ id: 2, kundeId: 7, rechnungsadresseOverride: 'Adresse B' })));

        expect(result.current.kontextDaten.rechnungsadresse).toBe('Adresse B');
        expect(result.current.kontextDatenRef.current.rechnungsadresse).toBe('Adresse B');
    });

    it('nimmt nach einem Dokumentwechsel ohne gespeicherte Adresse wieder die Kundenadresse', async () => {
        vi.stubGlobal('fetch', antwortenMit({ '/api/kunden/7': { name: 'Max Mustermann', strasse: 'Musterweg 1', plz: '12345', ort: 'Musterstadt' } }));
        const { result } = renderHook(() => useDokumentKontext({ dokumentId: 1, dokumentTyp: 'RECHNUNG', setzeBetreff: vi.fn() }));

        await act(() => result.current.ladeFuerDokument(dokument({ kundeId: 7, rechnungsadresseOverride: 'Adresse A' })));
        await act(() => result.current.ladeFuerDokument(dokument({ id: 2, kundeId: 7 })));

        expect(result.current.adresseUserEditedRef.current).toBe(false);
        expect(result.current.kontextDaten.rechnungsadresse).toBe('Max Mustermann\nMusterweg 1\n12345 Musterstadt');
    });

    it('nimmt bei einem gebuchten Dokument nur dessen eigene Werte', async () => {
        const fetchMock = antwortenMit({});
        vi.stubGlobal('fetch', fetchMock);
        const { result } = renderHook(() => useDokumentKontext({ dokumentId: 1, dokumentTyp: 'RECHNUNG', setzeBetreff: vi.fn() }));

        await act(() => result.current.ladeFuerDokument(dokument({
            gebucht: true, kundeId: 7, kundenName: 'Max Mustermann', zahlungszielTage: 14, rechnungsadresseOverride: 'Musterweg 1',
        })));

        expect(result.current.kontextDaten).toEqual({ kundenName: 'Max Mustermann', zahlungsziel: 14 });
        expect(fetchMock).not.toHaveBeenCalledWith('/api/kunden/7');
        // Ein gespeicherter Adress-Override gilt als Nutzeraenderung.
        expect(result.current.adresseUserEditedRef.current).toBe(true);
    });

    it('gibt einen Netzwerkfehler beim Laden der Stammdaten an den Aufrufer weiter', async () => {
        vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('offline'))));
        vi.spyOn(console, 'error').mockImplementation(() => {});
        const { result } = renderHook(() => useDokumentKontext({ dokumentId: 1, dokumentTyp: 'RECHNUNG', setzeBetreff: vi.fn() }));

        await expect(result.current.ladeFuerDokument(dokument({ projektId: 3 }))).rejects.toThrow('offline');
    });
});
