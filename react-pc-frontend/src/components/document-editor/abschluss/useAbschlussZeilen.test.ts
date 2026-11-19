import { describe, it, expect } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { abschlussZeileAuftrag, useAbschlussZeilen } from './useAbschlussZeilen';
import type { DokumentStand } from '../useDokumentVerlauf';

describe('useAbschlussZeilen', () => {
    it('startet automatisch und haelt Ref und State gleich', () => {
        const { result } = renderHook(() => useAbschlussZeilen());
        expect(result.current.abschlussZeilen).toEqual({});

        act(() => result.current.setzeAbschlussZeilen({ differenz: { ausgeblendet: true, bezeichnung: ' ' } }));

        expect(result.current.abschlussZeilen).toEqual({ differenz: { ausgeblendet: true } });
        expect(result.current.abschlussZeilenRef.current).toEqual({ differenz: { ausgeblendet: true } });
    });
});

describe('abschlussZeileAuftrag (Schritt fuer Rueckgaengig)', () => {
    const stand = { abschlussZeilen: { dieseRechnung: { bezeichnung: 'A' } } } as unknown as DokumentStand;

    it('benennt den Schritt und setzt nur die eine Zeile', () => {
        const auftrag = abschlussZeileAuftrag({}, 'differenz', { ausgeblendet: true });

        expect(auftrag.bezeichnung).toBe('Zeile ausgeblendet');
        expect(auftrag.berechne(stand)).toEqual({
            abschlussZeilen: { dieseRechnung: { bezeichnung: 'A' }, differenz: { ausgeblendet: true } },
        });
    });

    it('nimmt eine wieder automatische Zeile heraus', () => {
        const auftrag = abschlussZeileAuftrag(stand.abschlussZeilen, 'dieseRechnung', undefined);

        expect(auftrag.bezeichnung).toBe('Zeilentext geändert');
        expect(auftrag.berechne(stand)).toEqual({ abschlussZeilen: {} });
    });
});
