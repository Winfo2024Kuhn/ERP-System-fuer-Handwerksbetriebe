import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from './ui/toast';
import { ConfirmProvider } from './ui/confirm-dialog';
import EingangsrechnungenTab from './EingangsrechnungenTab';

const rechnung = {
    id: 1,
    dokumentId: 10,
    lieferantId: 20,
    lieferantName: 'Musterstahl GmbH',
    dokumentNummer: 'RE-1001',
    dokumentDatum: '2026-10-01',
    zahlungsziel: '2026-10-31',
    betragNetto: 100,
    betragBrutto: 119,
    bezahlt: false,
    bezahltAm: null,
    bereitsGezahlt: false,
    zahlungsart: null,
    dateiname: 're-1001.pdf',
    pdfUrl: '/api/dokumente/re-1001.pdf',
    ueberfaellig: false,
    genehmigt: false,
    darfGenehmigen: true,
    skontoTage: null,
    skontoProzent: null,
    nettoTage: null,
    skontoFrist: null,
    skontoVerbleibendeTage: null,
    skontoAbgelaufen: false,
    tatsaechlichGezahlt: null,
    mitSkonto: false,
};

const renderTab = () =>
    render(
        <MemoryRouter>
            <ToastProvider>
                <ConfirmProvider>
                    <EingangsrechnungenTab />
                </ConfirmProvider>
            </ToastProvider>
        </MemoryRouter>,
    );

describe('EingangsrechnungenTab – Genehmigen ohne Recht', () => {
    beforeEach(() => {
        vi.stubGlobal('fetch', vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
            if (init?.method === 'PATCH') {
                return new Response(null, { status: 403 });
            }
            return new Response(JSON.stringify([rechnung]), { status: 200 });
        }));
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('nennt das Abteilungsrecht statt einer fest eingebauten Abteilung', async () => {
        // Wer genehmigen darf, steuert das Recht „Rechnungen genehmigen“ je
        // Abteilung (Einstellungen → Berechtigungen) – nicht der Name „Büro“.
        const user = userEvent.setup();
        renderTab();

        const haken = await screen.findByRole('checkbox', { name: 'Rechnung genehmigen' });
        await user.click(haken);

        const meldung = await screen.findByRole('alert');
        await waitFor(() => expect(meldung).toHaveTextContent('Rechnungen genehmigen'));
        expect(meldung).toHaveTextContent('Berechtigungen');
        expect(meldung).not.toHaveTextContent(/Abteilung Büro/);
    });
});

describe('EingangsrechnungenTab – Fehler beim Speichern und Laden', () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    /** Liefert die Liste per GET; PUT/PATCH auf die Haken antworten wie übergeben. */
    const stubFetch = (hakenAntwort: () => Promise<Response>, listenAntwort = () => new Response(JSON.stringify([rechnung]))) => {
        const fetchMock = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) =>
            init?.method === 'PUT' || init?.method === 'PATCH' ? hakenAntwort() : listenAntwort());
        vi.stubGlobal('fetch', fetchMock);
        return fetchMock;
    };
    const listenAbrufe = (fetchMock: ReturnType<typeof stubFetch>) =>
        fetchMock.mock.calls.filter(([url]) => String(url) === '/api/offene-posten/eingang').length;

    it('zeigt beim Bezahlt-Haken die Meldung vom Server und lädt die Liste neu', async () => {
        const user = userEvent.setup();
        const fetchMock = stubFetch(async () => new Response(
            JSON.stringify({ status: 409, message: 'Jemand anders hat diese Daten gerade gespeichert.' }),
            { status: 409 },
        ));
        renderTab();

        await user.click(await screen.findByRole('checkbox', { name: 'Offen' }));

        expect(await screen.findByText('Jemand anders hat diese Daten gerade gespeichert.')).toBeInTheDocument();
        const put = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT');
        expect(String(put?.[0])).toBe('/api/offene-posten/eingang/1/bezahlt');
        expect(put?.[1]?.body).toBe(JSON.stringify({ bezahlt: true }));
        await waitFor(() => expect(listenAbrufe(fetchMock)).toBe(2));
        const haken = await screen.findByRole('checkbox', { name: 'Offen' });
        expect(haken).not.toBeChecked();
        expect(haken).toBeEnabled();
    });

    it('nimmt beim Genehmigen einen deutschen Ersatztext statt Springs Statustext', async () => {
        const user = userEvent.setup();
        stubFetch(async () => new Response(JSON.stringify({
            timestamp: '2026-10-12T19:00:00.000+00:00',
            status: 500,
            error: 'Internal Server Error',
            path: '/api/offene-posten/eingang/1/genehmigen',
        }), { status: 500 }));
        renderTab();

        await user.click(await screen.findByRole('checkbox', { name: 'Rechnung genehmigen' }));

        expect(await screen.findByText('Die Rechnung konnte nicht genehmigt werden.')).toBeInTheDocument();
        expect(screen.queryByText('Internal Server Error')).not.toBeInTheDocument();
    });

    it('meldet einen Verbindungsfehler beim Bezahlt-Haken, ohne neu zu laden', async () => {
        const user = userEvent.setup();
        const fetchMock = stubFetch(async () => { throw new TypeError('Failed to fetch'); });
        renderTab();

        await user.click(await screen.findByRole('checkbox', { name: 'Offen' }));

        expect(await screen.findByText(
            'Die Rechnung konnte nicht als bezahlt markiert werden. Bitte die Verbindung prüfen und es noch einmal versuchen.'
        )).toBeInTheDocument();
        expect(listenAbrufe(fetchMock)).toBe(1);
        expect(screen.getByRole('checkbox', { name: 'Offen' })).toBeEnabled();
    });

    it('zeigt einen Ladefehler als Fehler und nicht als „alles bezahlt“', async () => {
        const user = userEvent.setup();
        let listeKlappt = false;
        const fetchMock = stubFetch(
            async () => new Response(null, { status: 204 }),
            () => listeKlappt ? new Response(JSON.stringify([rechnung])) : new Response(null, { status: 500 }),
        );
        renderTab();

        expect(await screen.findByText(
            'Die Eingangsrechnungen konnten nicht geladen werden. Bitte auf „Aktualisieren“ klicken.'
        )).toBeInTheDocument();
        expect(screen.getByText('Die Eingangsrechnungen konnten nicht geladen werden.')).toBeInTheDocument();
        expect(screen.queryByText('Keine offenen Eingangsrechnungen')).not.toBeInTheDocument();
        expect(screen.queryByText('Offene Summe')).not.toBeInTheDocument();

        listeKlappt = true;
        await user.click(screen.getByRole('button', { name: /Aktualisieren/ }));

        expect(await screen.findByText('RE-1001')).toBeInTheDocument();
        expect(listenAbrufe(fetchMock)).toBe(2);
    });
});
