import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup, fireEvent, waitFor, within } from '@testing-library/react';
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

    fireEvent.click(await screen.findByRole('checkbox', { name: 'Bezahlt' }));

    expect(await screen.findByText('Jemand anders hat diese Daten gerade gespeichert.')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/projekte/dokumente/7/bezahlt?bezahlt=true', { method: 'PATCH' });
    // Liste neu geladen, Haken zeigt weiter den gespeicherten Stand (offen).
    await waitFor(() => expect(listenAbrufe(fetchMock)).toBe(2));
    const haken = await screen.findByRole('checkbox', { name: 'Bezahlt' });
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

    fireEvent.click(await screen.findByRole('checkbox', { name: 'Bezahlt' }));

    expect(await screen.findByText('Die Rechnung konnte nicht als bezahlt markiert werden.')).toBeInTheDocument();
    expect(screen.queryByText('Internal Server Error')).not.toBeInTheDocument();
});

it('meldet einen Verbindungsfehler und lässt den Haken unverändert', async () => {
    const fetchMock = stubFetch(async () => { throw new TypeError('Failed to fetch'); });
    zeigeSeite();

    fireEvent.click(await screen.findByRole('checkbox', { name: 'Bezahlt' }));

    expect(await screen.findByText(
        'Die Rechnung konnte nicht als bezahlt markiert werden. Bitte die Verbindung prüfen und es noch einmal versuchen.'
    )).toBeInTheDocument();
    const haken = screen.getByRole('checkbox', { name: 'Bezahlt' });
    expect(haken).not.toBeChecked();
    expect(haken).toBeEnabled();
    expect(listenAbrufe(fetchMock)).toBe(1);
});

const bezahlteRechnung: OffenerPosten = {
    id: 9,
    rechnungsnummer: 'RE-2026-002',
    rechnungsdatum: '2026-09-15',
    rechnungsbetrag: 500,
    bezahlt: true,
    mahnung: false,
    projektKunde: 'Erika Musterfrau',
};

/** Server-Stand mit offener und bezahlter Rechnung; PATCH verschiebt die Rechnung zwischen den Listen. */
const stubServer = (patchAntwort?: () => Partial<Response>) => {
    const stand = { offen: [offeneRechnung], bezahlt: [bezahlteRechnung] };
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
        if (init?.method === 'PATCH') {
            if (patchAntwort) return patchAntwort();
            const [, idText, wert] = /dokumente\/(\d+)\/bezahlt\?bezahlt=(true|false)/.exec(url)!;
            const id = Number(idText);
            const alle = [...stand.offen, ...stand.bezahlt];
            const rechnung = { ...alle.find(r => r.id === id)!, bezahlt: wert === 'true' };
            stand.offen = stand.offen.filter(r => r.id !== id);
            stand.bezahlt = stand.bezahlt.filter(r => r.id !== id);
            (rechnung.bezahlt ? stand.bezahlt : stand.offen).push(rechnung);
            return { ok: true, status: 204, json: async () => ({}) };
        }
        if (url === '/api/projekte/offene-posten') return { ok: true, json: async () => stand.offen };
        if (url === '/api/projekte/offene-posten/bezahlt') return { ok: true, json: async () => stand.bezahlt };
        return { ok: true, json: async () => [] };
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
};

it('zeigt bezahlte Rechnungen erst mit „Bezahlte anzeigen“ und rechnet sie nicht in die offene Summe', async () => {
    const fetchMock = stubServer();
    zeigeSeite();

    expect(await screen.findByText('RE-2026-001')).toBeInTheDocument();
    expect(screen.queryByText('RE-2026-002')).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url]) => url === '/api/projekte/offene-posten/bezahlt')).toBe(false);

    fireEvent.click(screen.getByRole('checkbox', { name: 'Bezahlte anzeigen' }));

    const zeile = (await screen.findByText('RE-2026-002')).closest('tr')!;
    expect(zeile).toHaveAttribute('data-bezahlt', 'true');
    expect(within(zeile).getByRole('checkbox', { name: 'Bezahlt' })).toBeChecked();
    expect(screen.getByText(/Bezahlt \(letzte 12 Monate\)/)).toBeInTheDocument();
    // Offene Summe bleibt bei der offenen Rechnung (1.190,00 €), die bezahlte zählt nicht mit.
    expect(screen.getByText('Offene Summe').nextElementSibling).toHaveTextContent('1.190,00 €');

    fireEvent.click(screen.getByRole('checkbox', { name: 'Bezahlte anzeigen' }));
    await waitFor(() => expect(screen.queryByText('RE-2026-002')).not.toBeInTheDocument());
});

it('nimmt den Haken „Bezahlt“ zurück: die Rechnung ist wieder offen', async () => {
    const fetchMock = stubServer();
    zeigeSeite();
    fireEvent.click(await screen.findByRole('checkbox', { name: 'Bezahlte anzeigen' }));
    const zeile = (await screen.findByText('RE-2026-002')).closest('tr')!;

    fireEvent.click(within(zeile).getByRole('checkbox', { name: 'Bezahlt' }));

    expect(await screen.findByText('Die Rechnung ist wieder offen.')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/api/projekte/dokumente/9/bezahlt?bezahlt=false', { method: 'PATCH' });
    await waitFor(() => expect(screen.getByText('RE-2026-002').closest('tr')).not.toHaveAttribute('data-bezahlt'));
    expect(within(screen.getByText('RE-2026-002').closest('tr')!).getByRole('checkbox', { name: 'Bezahlt' })).not.toBeChecked();
});

it('bestätigt das Abhaken und sagt, wo man es zurücknimmt', async () => {
    stubServer();
    zeigeSeite();
    fireEvent.click(await screen.findByRole('checkbox', { name: 'Bezahlt' }));
    expect(await screen.findByText('Als bezahlt markiert. Über „Bezahlte anzeigen“ können Sie den Haken wieder herausnehmen.')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText('RE-2026-001')).not.toBeInTheDocument());
});

it('zeigt die Meldung vom Server, wenn das Zurücknehmen abgelehnt wird', async () => {
    stubServer(() => ({ ok: false, status: 400, json: async () => ({ status: 400, message: 'Eine stornierte Rechnung kann nicht wieder offen sein.' }) }));
    zeigeSeite();
    fireEvent.click(await screen.findByRole('checkbox', { name: 'Bezahlte anzeigen' }));
    const zeile = (await screen.findByText('RE-2026-002')).closest('tr')!;
    fireEvent.click(within(zeile).getByRole('checkbox', { name: 'Bezahlt' }));
    expect(await screen.findByText('Eine stornierte Rechnung kann nicht wieder offen sein.')).toBeInTheDocument();
    expect(screen.queryByText('Die Rechnung ist wieder offen.')).not.toBeInTheDocument();
});

it('erklärt „Bezahlte anzeigen“ per Info-Knopf', async () => {
    stubServer();
    zeigeSeite();
    fireEvent.click(await screen.findByRole('button', { name: 'Mehr zu: Bezahlte anzeigen' }));
    const info = screen.getByRole('dialog', { name: 'Bezahlte anzeigen' });
    expect(info).toHaveTextContent('Stornierte Rechnungen erscheinen hier nicht.');
    expect(info).toHaveTextContent('Die Rechnung selbst und ihre Nummer bleiben unverändert.');
});

it('zeigt bei einer Gutschrift den offenen Betrag und rechnet die Summe damit', async () => {
    const mitGutschrift: OffenerPosten = {
        ...offeneRechnung, gutgeschrieben: 119, offenerBetrag: 1071,
    };
    const ohneOffenenBetrag: OffenerPosten = {
        id: 8, rechnungsnummer: 'RE-2026-003', rechnungsdatum: '2026-10-02', rechnungsbetrag: 500,
        bezahlt: false, mahnung: false, projektKunde: 'Musterfirma GmbH', gutgeschrieben: 0, offenerBetrag: null,
    };
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
        if (url === '/api/projekte/offene-posten') return { ok: true, json: async () => [mitGutschrift, ohneOffenenBetrag] };
        return { ok: true, json: async () => [] };
    }));
    zeigeSeite();

    const zeile = (await screen.findByText('RE-2026-001')).closest('tr')!;
    expect(within(zeile).getByText('1.071,00 €', { exact: false })).toBeInTheDocument();
    expect(within(zeile).getByText('Rechnung 1.190,00 € · Gutschrift −119,00 €')).toBeInTheDocument();
    // Ohne Gutschrift keine Zusatzzeile; offenerBetrag null -> Rechnungsbetrag
    const zweite = screen.getByText('RE-2026-003').closest('tr')!;
    expect(within(zweite).queryByText(/Gutschrift/)).not.toBeInTheDocument();
    expect(within(zweite).getByText('500,00 €')).toBeInTheDocument();
    // 1.071 + 500
    expect(screen.getByText('Offene Summe').nextElementSibling).toHaveTextContent('1.571,00 €');
});
