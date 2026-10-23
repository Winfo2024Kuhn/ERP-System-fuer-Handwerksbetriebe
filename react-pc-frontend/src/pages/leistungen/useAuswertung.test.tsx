import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuswertung } from './useAuswertung';
import type { AuswertungZeile } from './typen';

const ladeAuswertung = vi.fn();
vi.mock('./api', () => ({ ladeAuswertung: (...args: unknown[]) => ladeAuswertung(...args) }));

const ZEILE: AuswertungZeile = {
    kategorieId: 1, bezeichnung: 'Metallbau', bildUrl: null, verrechnungseinheit: 'Stück', leaf: false,
    projektAnzahl: 3, stunden: 18, menge: 2, stundenProEinheit: 1.5, leistungAnzahl: 6,
};

describe('useAuswertung', () => {
    beforeEach(() => ladeAuswertung.mockReset());

    it('lädt Zeilen für Jahr und Ordner und lädt auf Wunsch erneut', async () => {
        ladeAuswertung.mockResolvedValue([ZEILE]);
        const { result } = renderHook(() => useAuswertung('2026', 1));
        await waitFor(() => expect(result.current.laedt).toBe(false));
        expect(result.current.zeilen).toEqual([ZEILE]);
        expect(ladeAuswertung).toHaveBeenCalledWith('2026', 1);

        act(() => result.current.erneut());
        await waitFor(() => expect(ladeAuswertung).toHaveBeenCalledTimes(2));
    });

    it('zeigt die Fehlermeldung oder einen Ersatztext', async () => {
        ladeAuswertung.mockRejectedValueOnce(new Error('Bitte ein Jahr zwischen 2000 und 2100 wählen.'));
        const { result, rerender } = renderHook(({ jahr }) => useAuswertung(jahr, null), { initialProps: { jahr: '1999' } });
        await waitFor(() => expect(result.current.fehler).toBe('Bitte ein Jahr zwischen 2000 und 2100 wählen.'));
        expect(result.current.zeilen).toBeNull();

        ladeAuswertung.mockRejectedValueOnce('kaputt');
        rerender({ jahr: '' });
        await waitFor(() => expect(result.current.fehler).toBe('Die Auswertung konnte nicht geladen werden.'));
    });

    it('lädt nichts, solange sie nicht aktiv ist', () => {
        const { result } = renderHook(() => useAuswertung('', null, false));
        expect(ladeAuswertung).not.toHaveBeenCalled();
        expect(result.current.laedt).toBe(false);
    });

    it('verwirft veraltete Antworten nach einem Jahreswechsel', async () => {
        let alteAntwort: (z: AuswertungZeile[]) => void = () => {};
        let alterFehler: (e: Error) => void = () => {};
        ladeAuswertung
            .mockReturnValueOnce(new Promise((ok) => { alteAntwort = ok; }))
            .mockReturnValueOnce(new Promise((_, nein) => { alterFehler = nein; }))
            .mockResolvedValueOnce([ZEILE]);
        const { result, rerender } = renderHook(({ jahr }) => useAuswertung(jahr, null), { initialProps: { jahr: '2024' } });
        rerender({ jahr: '2025' });
        rerender({ jahr: '2026' });
        await waitFor(() => expect(result.current.zeilen).toEqual([ZEILE]));

        await act(async () => {
            alteAntwort([{ ...ZEILE, bezeichnung: 'veraltet' }]);
            alterFehler(new Error('veraltet'));
        });
        expect(result.current.zeilen).toEqual([ZEILE]);
        expect(result.current.fehler).toBeNull();
    });
});
