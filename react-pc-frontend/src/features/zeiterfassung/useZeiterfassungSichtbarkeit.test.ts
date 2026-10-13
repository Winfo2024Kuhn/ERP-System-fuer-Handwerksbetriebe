import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { antwort, aufrufe, stubbeFetch } from '../telefon/telefonTestdaten';
import { setzeZeiterfassungSichtbarkeitZurueck, useZeiterfassungSichtbarkeit } from './useZeiterfassungSichtbarkeit';

const PFAD = '/api/zeitverwaltung/sichtbarkeit';

describe('useZeiterfassungSichtbarkeit', () => {
    beforeEach(() => setzeZeiterfassungSichtbarkeitZurueck());
    afterEach(() => vi.unstubAllGlobals());

    it('liefert erst null (lädt), dann das Recht – und fragt nur einmal für alle Stellen', async () => {
        const fetchMock = stubbeFetch((url) => (url.pathname === PFAD ? antwort({ darfZeiterfassungSehen: true }) : undefined));
        const a = renderHook(() => useZeiterfassungSichtbarkeit());
        const b = renderHook(() => useZeiterfassungSichtbarkeit());
        expect(a.result.current).toBeNull();
        await waitFor(() => expect(a.result.current).toBe(true));
        expect(b.result.current).toBe(true);
        expect(aufrufe(fetchMock, PFAD)).toHaveLength(1);
    });

    it('liefert false, wenn die Abteilung den Haken nicht hat', async () => {
        stubbeFetch((url) => (url.pathname === PFAD ? antwort({ darfZeiterfassungSehen: false }) : undefined));
        const { result } = renderHook(() => useZeiterfassungSichtbarkeit());
        await waitFor(() => expect(result.current).toBe(false));
    });

    it('wertet Serverfehler, fehlende Anmeldung und kaputte Antworten als "kein Recht"', async () => {
        stubbeFetch(() => antwort({}, 401));
        const unangemeldet = renderHook(() => useZeiterfassungSichtbarkeit());
        await waitFor(() => expect(unangemeldet.result.current).toBe(false));

        act(() => setzeZeiterfassungSichtbarkeitZurueck());
        stubbeFetch(() => antwort({}, 500));
        await waitFor(() => expect(unangemeldet.result.current).toBe(false));

        act(() => setzeZeiterfassungSichtbarkeitZurueck());
        stubbeFetch(() => Promise.reject(new Error('Netz weg')) as unknown as Response);
        await waitFor(() => expect(unangemeldet.result.current).toBe(false));
    });

    it('fragt nach dem Zurücksetzen (Benutzerwechsel) neu', async () => {
        let darf = false;
        const fetchMock = stubbeFetch(() => antwort({ darfZeiterfassungSehen: darf }));
        const { result } = renderHook(() => useZeiterfassungSichtbarkeit());
        await waitFor(() => expect(result.current).toBe(false));
        darf = true;
        act(() => setzeZeiterfassungSichtbarkeitZurueck());
        await waitFor(() => expect(result.current).toBe(true));
        expect(aufrufe(fetchMock, PFAD)).toHaveLength(2);
    });
});
