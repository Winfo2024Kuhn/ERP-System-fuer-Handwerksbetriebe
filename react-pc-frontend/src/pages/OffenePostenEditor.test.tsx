import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { ReactNode } from 'react';
import OffenePostenEditor from './OffenePostenEditor';
import { ToastProvider } from '../components/ui/toast';
import type { OffenerPosten } from '../types';

vi.mock('../components/layout/PageLayout', () => ({ PageLayout: ({ children, actions }: {
        children: ReactNode;
        actions: ReactNode;
    }) => <div>{actions}{children}</div> }));

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

const offeneRechnung: OffenerPosten = {
    id: 7,
    rechnungsnummer: 'RE-2026-001',
    rechnungsdatum: '2026-10-01',
    rechnungsbetrag: 1190,
    bezahlt: false,
    mahnung: false,
    projektKunde: 'Max Mustermann',
};

const zeigeSeite = () => render(
    <ToastProvider><MemoryRouter><OffenePostenEditor /></MemoryRouter></ToastProvider>
);

/** Liefert die Liste per GET; der PATCH auf den Bezahlt-Haken antwortet wie übergeben. */
const stubFetch = (bezahltAntwort: () => Promise<Partial<Response>>) => {
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
        if (init?.method === 'PATCH') return bezahltAntwort();
        if (url === '/api/projekte/offene-posten') return { ok: true, json: async () => [offeneRechnung] };
        return { ok: true, json: async () => [] };
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
};

const listenAbrufe = (fetchMock: ReturnType<typeof stubFetch>) =>
    fetchMock.mock.calls.filter(([url]) => url === '/api/projekte/offene-posten').length;

it('bietet kein manuelles Erfassen von Ausgangsrechnungen mehr an', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, json: async () => [] })));
    zeigeSeite();
    expect(await screen.findByRole('button', { name: /aktualisieren/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /manuell erfassen/i })).not.toBeInTheDocument();
});

it('zeigt die Meldung vom Server, wenn der Bezahlt-Haken nicht gespeichert werden kann', async () => {
    const fetchMock = stubFetch(async () => ({
        ok: false,
        status: 409,
        json: async () => ({ status: 409, message: 'Jemand anders hat diese Daten gerade gespeichert.' }),
    }));
    zeigeSeite();

    fireEvent.click(await screen.findByRole('checkbox', { name: /bezahlt/i }));

    expect(await screen.findByText('Jemand anders hat diese Daten gerade gespeichert.')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/projekte/dokumente/7/bezahlt?bezahlt=true', { method: 'PATCH' });
    // Liste neu geladen, Haken zeigt weiter den gespeicherten Stand (offen).
    await waitFor(() => expect(listenAbrufe(fetchMock)).toBe(2));
    const haken = await screen.findByRole('checkbox', { name: /bezahlt/i });
    expect(haken).not.toBeChecked();
    expect(haken).toBeEnabled();
});

it('nimmt einen verständlichen Ersatztext statt Springs englischem Statustext', async () => {
    stubFetch(async () => ({
        ok: false,
        status: 500,
        json: async () => ({
            timestamp: '2026-10-12T19:00:00.000+00:00',
            status: 500,
            error: 'Internal Server Error',
            path: '/api/projekte/dokumente/7/bezahlt',
        }),
    }));
    zeigeSeite();

    fireEvent.click(await screen.findByRole('checkbox', { name: /bezahlt/i }));

    expect(await screen.findByText('Die Rechnung konnte nicht als bezahlt markiert werden.')).toBeInTheDocument();
    expect(screen.queryByText('Internal Server Error')).not.toBeInTheDocument();
});

it('meldet einen Verbindungsfehler und lässt den Haken unverändert', async () => {
    const fetchMock = stubFetch(async () => { throw new TypeError('Failed to fetch'); });
    zeigeSeite();

    fireEvent.click(await screen.findByRole('checkbox', { name: /bezahlt/i }));

    expect(await screen.findByText(
        'Die Rechnung konnte nicht als bezahlt markiert werden. Bitte die Verbindung prüfen und es noch einmal versuchen.'
    )).toBeInTheDocument();
    const haken = screen.getByRole('checkbox', { name: /bezahlt/i });
    expect(haken).not.toBeChecked();
    expect(haken).toBeEnabled();
    expect(listenAbrufe(fetchMock)).toBe(1);
});
