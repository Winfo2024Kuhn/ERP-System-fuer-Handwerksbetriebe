import { act, renderHook, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { antwort, aufrufe, stubbeFetch } from './telefonTestdaten';
import { useAnrufen } from './useAnrufen';
import { WAEHL_TELEFON_SCHLUESSEL } from './useWaehlTelefon';

const TELEFONE = [{ name: 'LAN: PC Büro' }, { name: 'DECT: Mobilteil Büro' }];
const UNBEKANNT = 'Dieses Telefon kennt die FRITZ!Box nicht. Bitte ein anderes Telefon auswählen.';

function wrapper({ children }: { children: ReactNode }) {
    return <ToastProvider>{children}</ToastProvider>;
}

function stubbe(anrufAntwort: () => Response | Promise<Response>, telefone = TELEFONE) {
    return stubbeFetch(
        (url) => (url.pathname === '/api/telefon/telefone' ? antwort(telefone) : undefined),
        (url, init) => (url.pathname === '/api/telefon/anrufen' && init?.method === 'POST' ? anrufAntwort() : undefined),
    );
}

describe('useAnrufen', () => {
    afterEach(() => {
        vi.unstubAllGlobals();
        window.localStorage.clear();
    });

    it('fragt ohne gespeichertes Telefon erst nach dem Telefon und ruft danach an', async () => {
        const fetchMock = stubbe(() => antwort(undefined, 204));
        const { result } = renderHook(() => useAnrufen(), { wrapper });

        act(() => result.current.anrufen('0931 1234567'));
        expect(result.current.auswahlOffen).toBe(true);
        expect(aufrufe(fetchMock, '/api/telefon/anrufen')).toHaveLength(0);

        act(() => result.current.telefonGewaehlt('LAN: PC Büro'));
        expect(result.current.auswahlOffen).toBe(false);
        expect(window.localStorage.getItem(WAEHL_TELEFON_SCHLUESSEL)).toBe('LAN: PC Büro');

        await waitFor(() => expect(aufrufe(fetchMock, '/api/telefon/anrufen', 'POST')).toHaveLength(1));
        const body = JSON.parse(String(aufrufe(fetchMock, '/api/telefon/anrufen', 'POST')[0][1]?.body));
        expect(body).toEqual({ telefon: 'LAN: PC Büro', nummer: '0931 1234567' });
        expect(await screen.findByText('Ihr Telefon klingelt – abnehmen, dann wird verbunden.')).toBeInTheDocument();
    });

    it('ruft mit gespeichertem Telefon sofort an und sperrt Doppelklicks, solange es läuft', async () => {
        window.localStorage.setItem(WAEHL_TELEFON_SCHLUESSEL, 'LAN: PC Büro');
        let freigeben: (r: Response) => void = () => undefined;
        const fetchMock = stubbe(() => new Promise<Response>((r) => { freigeben = r; }));
        const { result } = renderHook(() => useAnrufen(), { wrapper });

        act(() => result.current.anrufen('0931 1234567'));
        expect(result.current.auswahlOffen).toBe(false);
        await waitFor(() => expect(result.current.laeuft).toBe(true));
        act(() => result.current.anrufen('0931 1234567'));
        expect(aufrufe(fetchMock, '/api/telefon/anrufen', 'POST')).toHaveLength(1);

        await act(async () => { freigeben(antwort(undefined, 204)); });
        await waitFor(() => expect(result.current.laeuft).toBe(false));
        expect(screen.getByText('Ihr Telefon klingelt – abnehmen, dann wird verbunden.')).toBeInTheDocument();
    });

    it('meldet einen Fehler der FRITZ!Box genau mit dem Text vom Server', async () => {
        window.localStorage.setItem(WAEHL_TELEFON_SCHLUESSEL, 'LAN: PC Büro');
        const meldung = 'Die FRITZ!Box konnte nicht wählen. Bitte prüfen, ob die Wählhilfe eingeschaltet ist (FRITZ!Box: Telefonie → Anrufe → Wählhilfe).';
        stubbe(() => antwort({ message: meldung }, 502));
        const onGestartet = vi.fn();
        const { result } = renderHook(() => useAnrufen(onGestartet), { wrapper });

        act(() => result.current.anrufen('0931 1234567'));
        expect(await screen.findByText(meldung)).toBeInTheDocument();
        expect(result.current.auswahlOffen).toBe(false);
        expect(onGestartet).not.toHaveBeenCalled();
        expect(window.localStorage.getItem(WAEHL_TELEFON_SCHLUESSEL)).toBe('LAN: PC Büro');
    });

    it('bietet die Auswahl erneut an, wenn die FRITZ!Box das gespeicherte Telefon nicht mehr kennt', async () => {
        window.localStorage.setItem(WAEHL_TELEFON_SCHLUESSEL, 'LAN: Altes Telefon');
        stubbe(() => antwort({ message: UNBEKANNT }, 400));
        const { result } = renderHook(() => useAnrufen(), { wrapper });

        act(() => result.current.anrufen('0931 1234567'));
        expect(await screen.findByText(UNBEKANNT)).toBeInTheDocument();
        await waitFor(() => expect(result.current.auswahlOffen).toBe(true));
        expect(window.localStorage.getItem(WAEHL_TELEFON_SCHLUESSEL)).toBeNull();
    });

    it('lässt das Telefon stehen, wenn nur die Nummer nicht wählbar ist', async () => {
        window.localStorage.setItem(WAEHL_TELEFON_SCHLUESSEL, 'LAN: PC Büro');
        stubbe(() => antwort({ message: 'Diese Nummer kann nicht gewählt werden.' }, 400));
        const { result } = renderHook(() => useAnrufen(), { wrapper });

        act(() => result.current.anrufen('**610'));
        expect(await screen.findByText('Diese Nummer kann nicht gewählt werden.')).toBeInTheDocument();
        await waitFor(() => expect(result.current.laeuft).toBe(false));
        expect(result.current.auswahlOffen).toBe(false);
        expect(window.localStorage.getItem(WAEHL_TELEFON_SCHLUESSEL)).toBe('LAN: PC Büro');
    });

    it('öffnet bei 409 (Anruf wird gerade schon aufgebaut) keine Telefon-Auswahl', async () => {
        window.localStorage.setItem(WAEHL_TELEFON_SCHLUESSEL, 'LAN: PC Büro');
        const meldung = 'Es wird gerade schon ein Anruf aufgebaut. Bitte gleich noch einmal versuchen.';
        const fetchMock = stubbe(() => antwort({ message: meldung }, 409));
        const { result } = renderHook(() => useAnrufen(), { wrapper });

        act(() => result.current.anrufen('0931 1234567'));
        expect(await screen.findByText(meldung)).toBeInTheDocument();
        await waitFor(() => expect(result.current.laeuft).toBe(false));
        expect(result.current.auswahlOffen).toBe(false);
        expect(aufrufe(fetchMock, '/api/telefon/telefone')).toHaveLength(0);
        expect(window.localStorage.getItem(WAEHL_TELEFON_SCHLUESSEL)).toBe('LAN: PC Büro');
    });

    it('meldet nach dem Anrufen den Start, damit das Anruf-Fenster sich schließen kann', async () => {
        window.localStorage.setItem(WAEHL_TELEFON_SCHLUESSEL, 'LAN: PC Büro');
        stubbe(() => antwort(undefined, 204));
        const onGestartet = vi.fn();
        const { result } = renderHook(() => useAnrufen(onGestartet), { wrapper });
        act(() => result.current.anrufen('0931 1234567'));
        await waitFor(() => expect(onGestartet).toHaveBeenCalledTimes(1));
    });

    it('schließt die Auswahl beim Abbrechen, ohne anzurufen', () => {
        const fetchMock = stubbe(() => antwort(undefined, 204));
        const { result } = renderHook(() => useAnrufen(), { wrapper });
        act(() => result.current.anrufen('0931 1234567'));
        act(() => result.current.auswahlAbbrechen());
        expect(result.current.auswahlOffen).toBe(false);
        expect(aufrufe(fetchMock, '/api/telefon/anrufen')).toHaveLength(0);
    });
});
