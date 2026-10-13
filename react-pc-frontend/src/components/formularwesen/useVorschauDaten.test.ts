import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import type { Kunde, Projekt } from '../../types';
import { SAMPLE_DATA } from './constants';
import { baueVorschauDaten, useVorschauDaten } from './useVorschauDaten';

const KUNDE = { id: 1, name: 'Mustermann GmbH', kundennummer: 'K-4711', anrede: 'Herr', ansprechpartner: 'Max Mustermann', strasse: 'Musterweg 1', plz: '12345', ort: 'Musterstadt' } as unknown as Kunde;
const PROJEKT = { id: 5, bauvorhaben: 'Dachsanierung', auftragsnummer: 'A-2026-005', anlegedatum: '2026-10-01' } as unknown as Projekt;

describe('baueVorschauDaten', () => {
    it('ohne Kunde und Projekt: Musterdaten', () => {
        expect(baueVorschauDaten(null, null)).toBe(SAMPLE_DATA);
    });

    it('setzt Anschrift, Nummern und Datum aus Kunde und Projekt', () => {
        expect(baueVorschauDaten(KUNDE, PROJEKT)).toMatchObject({
            kundenname: 'Mustermann GmbH',
            kundenadresse: 'Mustermann GmbH\nMax Mustermann\nMusterweg 1\n12345 Musterstadt',
            kundennummer: 'K-4711',
            anrede: 'Herr',
            ansprechpartner: 'Max Mustermann',
            bauvorhaben: 'Dachsanierung',
            dokumentnummer: 'A-2026-005',
            projektnummer: 'PRJ-5',
            datum: '1.10.2026',
        });
    });

    it('nur Projekt: Rest aus den Musterdaten, Datum heute falls keins da', () => {
        const daten = baueVorschauDaten(null, { id: 0, bauvorhaben: 'X' } as unknown as Projekt);
        expect(daten.kundenadresse).toBe(SAMPLE_DATA.kundenadresse);
        expect(daten.datum).toBe(new Date().toLocaleDateString('de-DE'));
    });
});

describe('useVorschauDaten', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('lädt einen Kunden und ein Projekt', async () => {
        vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve({
            ok: true,
            json: () => Promise.resolve(url.includes('kunden') ? { content: [KUNDE] } : [PROJEKT]),
        })));
        const { result } = renderHook(() => useVorschauDaten());
        await waitFor(() => expect(result.current.kundenname).toBe('Mustermann GmbH'));
        expect(result.current.dokumentnummer).toBe('A-2026-005');
    });

    it('behält die Musterdaten, wenn nichts geladen werden kann', async () => {
        const abruf = vi.fn(() => Promise.reject(new Error('offline')));
        vi.stubGlobal('fetch', abruf);
        const { result } = renderHook(() => useVorschauDaten());
        await waitFor(() => expect(abruf).toHaveBeenCalledTimes(2));
        expect(result.current).toEqual(SAMPLE_DATA);
    });
});
