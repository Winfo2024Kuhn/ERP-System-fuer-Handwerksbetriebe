import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { antwort, aufrufe, stubbeFetch } from './telefonTestdaten';
import { setzeTelefonBerechtigungZurueck } from './useTelefonBerechtigung';
import { WAEHL_TELEFON_SCHLUESSEL } from './useWaehlTelefon';
import { ZurueckrufenKnopf } from './ZurueckrufenKnopf';

const TELEFONE = [{ name: 'LAN: PC Büro' }, { name: 'DECT: Mobilteil Büro' }];

function stubbe(darf = true, anrufAntwort: () => Response | Promise<Response> = () => antwort(undefined, 204)) {
    return stubbeFetch(
        (url) => (url.pathname === '/api/telefon/berechtigung' ? antwort({ darfTelefonSehen: darf }) : undefined),
        (url) => (url.pathname === '/api/telefon/telefone' ? antwort(TELEFONE) : undefined),
        (url, init) => (url.pathname === '/api/telefon/anrufen' && init?.method === 'POST' ? anrufAntwort() : undefined),
    );
}

function zeige(nummer = '0931 1234567', gross = false) {
    return render(
        <ToastProvider>
            <ZurueckrufenKnopf nummer={nummer} wer="Max Mustermann" gross={gross} />
        </ToastProvider>,
    );
}

describe('ZurueckrufenKnopf', () => {
    beforeEach(() => setzeTelefonBerechtigungZurueck());
    afterEach(() => {
        vi.unstubAllGlobals();
        window.localStorage.clear();
    });

    it('fragt beim ersten Klick nach dem Telefon, ruft dann an – beim zweiten Klick ohne Frage', async () => {
        const fetchMock = stubbe();
        zeige();
        const knopf = await screen.findByRole('button', { name: 'Max Mustermann zurückrufen' });
        expect(knopf).toHaveAttribute('title', 'Zurückrufen');
        // Gemeinsamer Button (Ghost-Variante), kein eigener Knopf-Stil.
        expect(knopf).toHaveClass('bg-transparent', 'text-rose-700', 'h-8', 'w-8');
        expect(knopf).not.toHaveClass('px-3');

        fireEvent.click(knopf);
        const dialog = await screen.findByRole('dialog', { name: 'Welches Telefon steht an diesem Rechner?' });
        fireEvent.click(await within(dialog).findByRole('radio', { name: 'LAN: PC Büro' }));
        fireEvent.click(within(dialog).getByRole('button', { name: 'Anrufen' }));

        expect(await screen.findByText('Ihr Telefon klingelt – abnehmen, dann wird verbunden.')).toBeInTheDocument();
        expect(screen.queryByRole('dialog')).toBeNull();
        expect(window.localStorage.getItem(WAEHL_TELEFON_SCHLUESSEL)).toBe('LAN: PC Büro');

        fireEvent.click(screen.getByRole('button', { name: 'Max Mustermann zurückrufen' }));
        await waitFor(() => expect(aufrufe(fetchMock, '/api/telefon/anrufen', 'POST')).toHaveLength(2));
        expect(screen.queryByRole('dialog')).toBeNull();
        const bodies = aufrufe(fetchMock, '/api/telefon/anrufen', 'POST').map(([, init]) => JSON.parse(String(init?.body)));
        expect(bodies).toEqual([
            { telefon: 'LAN: PC Büro', nummer: '0931 1234567' },
            { telefon: 'LAN: PC Büro', nummer: '0931 1234567' },
        ]);
    });

    it('ist während der Anfrage gesperrt', async () => {
        window.localStorage.setItem(WAEHL_TELEFON_SCHLUESSEL, 'LAN: PC Büro');
        let freigeben: (r: Response) => void = () => undefined;
        stubbe(true, () => new Promise<Response>((r) => { freigeben = r; }));
        zeige();
        const knopf = await screen.findByRole('button', { name: 'Max Mustermann zurückrufen' });
        fireEvent.click(knopf);
        await waitFor(() => expect(knopf).toBeDisabled());
        expect(knopf).toHaveAttribute('aria-busy', 'true');
        freigeben(antwort(undefined, 204));
        await waitFor(() => expect(knopf).toBeEnabled());
    });

    it('zeigt als großer Knopf die Beschriftung „Zurückrufen“', async () => {
        stubbe();
        zeige('0931 1234567', true);
        const knopf = await screen.findByRole('button', { name: 'Max Mustermann zurückrufen' });
        expect(knopf).toHaveTextContent('Zurückrufen');
    });

    it('zeigt ohne Telefon-Recht keinen Knopf', async () => {
        const fetchMock = stubbe(false);
        zeige();
        await waitFor(() => expect(aufrufe(fetchMock, '/api/telefon/berechtigung')).toHaveLength(1));
        expect(screen.queryByRole('button')).toBeNull();
    });

    it('zeigt bei unterdrückter Nummer keinen Knopf', async () => {
        stubbe();
        zeige('   ');
        await waitFor(() => expect(screen.queryByRole('button')).toBeNull());
    });
});
