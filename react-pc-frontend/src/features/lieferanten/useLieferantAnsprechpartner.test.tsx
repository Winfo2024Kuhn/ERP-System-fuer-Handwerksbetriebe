import type { ReactNode } from 'react';
import { act, renderHook, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { useLieferantAnsprechpartner } from './useLieferantAnsprechpartner';
import { leererEntwurf, type LieferantAnsprechpartner } from './ansprechpartnerEntwurf';

/** Dummy-Daten (DSGVO): Max Mustermann, Erika Musterfrau. */
const wrapper = ({ children }: { children: ReactNode }) => <ToastProvider><ConfirmProvider>{children}</ConfirmProvider></ToastProvider>;

const leer = { anrede: null, vorname: null, aufgabe: null, telefon: null, mobiltelefon: null, email: null, notiz: null };
const MAX: LieferantAnsprechpartner = { ...leer, id: 1, vorname: 'Max', nachname: 'Mustermann', aufgabe: 'Außendienst' };
const ERIKA: LieferantAnsprechpartner = { ...leer, id: 2, vorname: 'Erika', nachname: 'Musterfrau' };

function antwort(body: unknown, status = 200): Response {
    return { ok: status < 400, status, json: () => Promise.resolve(body) } as Response;
}

afterEach(() => vi.unstubAllGlobals());

describe('useLieferantAnsprechpartner', () => {
    it('lädt die Liste des Lieferanten', async () => {
        const fetchMock = vi.fn().mockResolvedValue(antwort([MAX]));
        vi.stubGlobal('fetch', fetchMock);
        const { result } = renderHook(() => useLieferantAnsprechpartner(7), { wrapper });
        expect(result.current.liste).toBeUndefined();
        await waitFor(() => expect(result.current.liste).toEqual([MAX]));
        expect(fetchMock.mock.calls[0][0]).toBe('/api/lieferanten/7/ansprechpartner');
    });

    it('zeigt einen Fehler-Toast und den Fehlerzustand, wenn das Laden scheitert', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(antwort({ message: 'Lieferant nicht gefunden.' }, 404)));
        const { result } = renderHook(() => useLieferantAnsprechpartner(7), { wrapper });
        await waitFor(() => expect(result.current.fehler).toBe(true));
        expect(await screen.findByText('Lieferant nicht gefunden.')).toBeVisible();
    });

    it('legt an, sortiert nach Nachname und schickt leere Felder als null', async () => {
        const neu = { ...leer, id: 9, nachname: 'Anders', aufgabe: 'Buchhaltung' };
        const fetchMock = vi.fn().mockImplementation(async (_url: string, init?: RequestInit) =>
            init?.method === 'POST' ? antwort(neu, 201) : antwort([MAX]));
        vi.stubGlobal('fetch', fetchMock);
        const { result } = renderHook(() => useLieferantAnsprechpartner(7), { wrapper });
        await waitFor(() => expect(result.current.liste).toBeDefined());

        let ok = false;
        await act(async () => { ok = await result.current.speichern({ ...leererEntwurf(), nachname: ' Anders ', aufgabe: 'Buchhaltung' }, null); });

        expect(ok).toBe(true);
        const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');
        expect(post?.[0]).toBe('/api/lieferanten/7/ansprechpartner');
        expect(JSON.parse(String(post?.[1].body))).toEqual({ ...leer, nachname: 'Anders', aufgabe: 'Buchhaltung' });
        expect(result.current.liste?.map(a => a.nachname)).toEqual(['Anders', 'Mustermann']);
    });

    it('ändert per PUT und zeigt die Meldung des Servers bei Ablehnung', async () => {
        const fetchMock = vi.fn().mockImplementation(async (_url: string, init?: RequestInit) =>
            init?.method === 'PUT' ? antwort({ status: 400, message: 'Die E-Mail-Adresse ist ungültig.', fields: {} }, 400) : antwort([MAX]));
        vi.stubGlobal('fetch', fetchMock);
        const { result } = renderHook(() => useLieferantAnsprechpartner(7), { wrapper });
        await waitFor(() => expect(result.current.liste).toBeDefined());

        let ok = true;
        await act(async () => { ok = await result.current.speichern({ ...leererEntwurf(), nachname: 'Mustermann' }, 1); });

        expect(ok).toBe(false);
        expect(fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT')?.[0]).toBe('/api/lieferanten/7/ansprechpartner/1');
        expect(await screen.findByText('Die E-Mail-Adresse ist ungültig.')).toBeVisible();
        expect(result.current.liste).toEqual([MAX]);
    });

    it('löscht erst nach Rückfrage mit dem Namen der Person', async () => {
        const user = userEvent.setup();
        const fetchMock = vi.fn().mockImplementation(async (_url: string, init?: RequestInit) =>
            init?.method === 'DELETE' ? antwort(null, 204) : antwort([ERIKA, MAX]));
        vi.stubGlobal('fetch', fetchMock);
        const { result } = renderHook(() => useLieferantAnsprechpartner(7), { wrapper });
        await waitFor(() => expect(result.current.liste).toHaveLength(2));

        let fertig: Promise<void> = Promise.resolve();
        act(() => { fertig = result.current.loeschen(MAX); });
        expect(await screen.findByText(/Max Mustermann löschen\? Die Person verschwindet auch aus der Anruferkennung.*Das lässt sich nicht rückgängig machen\./)).toBeVisible();
        await user.click(screen.getByRole('button', { name: 'Ansprechpartner löschen' }));
        await act(() => fertig);

        expect(fetchMock.mock.calls.find(([, init]) => init?.method === 'DELETE')?.[0]).toBe('/api/lieferanten/7/ansprechpartner/1');
        expect(result.current.liste).toEqual([ERIKA]);
    });

    it('löscht nichts, wenn die Rückfrage abgebrochen wird', async () => {
        const user = userEvent.setup();
        const fetchMock = vi.fn().mockResolvedValue(antwort([MAX]));
        vi.stubGlobal('fetch', fetchMock);
        const { result } = renderHook(() => useLieferantAnsprechpartner(7), { wrapper });
        await waitFor(() => expect(result.current.liste).toHaveLength(1));

        let fertig: Promise<void> = Promise.resolve();
        act(() => { fertig = result.current.loeschen(MAX); });
        await user.click(await screen.findByRole('button', { name: 'Abbrechen' }));
        await act(() => fertig);

        expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false);
        expect(result.current.liste).toEqual([MAX]);
    });
});
