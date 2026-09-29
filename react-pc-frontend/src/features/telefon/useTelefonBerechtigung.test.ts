import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { antwort, aufrufe, stubbeFetch } from './telefonTestdaten';
import { setzeTelefonBerechtigungZurueck, useTelefonBerechtigung } from './useTelefonBerechtigung';

describe('useTelefonBerechtigung', () => {
    beforeEach(() => setzeTelefonBerechtigungZurueck());
    afterEach(() => vi.unstubAllGlobals());

    it('fragt einmal für alle Stellen und liefert das Recht', async () => {
        const fetchMock = stubbeFetch((url) => (url.pathname === '/api/telefon/berechtigung' ? antwort({ darfTelefonSehen: true }) : undefined));
        const a = renderHook(() => useTelefonBerechtigung());
        const b = renderHook(() => useTelefonBerechtigung());
        expect(a.result.current).toBeNull();
        await waitFor(() => expect(a.result.current).toBe(true));
        expect(b.result.current).toBe(true);
        expect(aufrufe(fetchMock, '/api/telefon/berechtigung')).toHaveLength(1);
    });

    it('wertet Fehler als "kein Recht"', async () => {
        stubbeFetch(() => antwort({}, 500));
        const { result } = renderHook(() => useTelefonBerechtigung());
        await waitFor(() => expect(result.current).toBe(false));
    });

    it('fragt nach dem Zurücksetzen neu', async () => {
        let darf = false;
        const fetchMock = stubbeFetch(() => antwort({ darfTelefonSehen: darf }));
        const { result } = renderHook(() => useTelefonBerechtigung());
        await waitFor(() => expect(result.current).toBe(false));
        darf = true;
        act(() => setzeTelefonBerechtigungZurueck());
        await waitFor(() => expect(result.current).toBe(true));
        expect(aufrufe(fetchMock, '/api/telefon/berechtigung')).toHaveLength(2);
    });
});
