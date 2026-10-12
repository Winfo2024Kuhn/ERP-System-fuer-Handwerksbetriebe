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
