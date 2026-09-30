import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { liesWaehlTelefon, useWaehlTelefon, WAEHL_TELEFON_SCHLUESSEL } from './useWaehlTelefon';

describe('useWaehlTelefon', () => {
    afterEach(() => window.localStorage.clear());

    it('hat ohne gespeicherte Auswahl kein Telefon', () => {
        const { result } = renderHook(() => useWaehlTelefon());
        expect(result.current.telefon).toBeNull();
        expect(liesWaehlTelefon()).toBeNull();
    });

    it('speichert die Auswahl pro Rechner im Browser und teilt sie mit allen Stellen', () => {
        const erste = renderHook(() => useWaehlTelefon());
        const zweite = renderHook(() => useWaehlTelefon());

        act(() => erste.result.current.setzeTelefon('LAN: PC Büro'));

        expect(window.localStorage.getItem(WAEHL_TELEFON_SCHLUESSEL)).toBe('LAN: PC Büro');
        expect(WAEHL_TELEFON_SCHLUESSEL).toBe('telefon.waehlTelefon');
        expect(erste.result.current.telefon).toBe('LAN: PC Büro');
        expect(zweite.result.current.telefon).toBe('LAN: PC Büro');
    });

    it('vergisst die Auswahl beim Zurücksetzen', () => {
        window.localStorage.setItem(WAEHL_TELEFON_SCHLUESSEL, 'DECT: Mobilteil Büro');
        const { result } = renderHook(() => useWaehlTelefon());
        expect(result.current.telefon).toBe('DECT: Mobilteil Büro');

        act(() => result.current.vergiss());

        expect(result.current.telefon).toBeNull();
        expect(window.localStorage.getItem(WAEHL_TELEFON_SCHLUESSEL)).toBeNull();
    });

    it('übernimmt Änderungen aus einem anderen Browser-Tab', () => {
        const { result } = renderHook(() => useWaehlTelefon());
        act(() => {
            window.localStorage.setItem(WAEHL_TELEFON_SCHLUESSEL, 'LAN: PC Werkstatt');
            window.dispatchEvent(new StorageEvent('storage', { key: WAEHL_TELEFON_SCHLUESSEL }));
        });
        expect(result.current.telefon).toBe('LAN: PC Werkstatt');
    });

    it('behandelt leere Einträge wie keine Auswahl', () => {
        window.localStorage.setItem(WAEHL_TELEFON_SCHLUESSEL, '   ');
        const { result } = renderHook(() => useWaehlTelefon());
        expect(result.current.telefon).toBeNull();
    });
});
