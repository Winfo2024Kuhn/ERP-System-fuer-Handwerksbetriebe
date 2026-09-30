import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { antwort, aufrufe, stubbeFetch } from './telefonTestdaten';
import { setzeTelefonBerechtigungZurueck } from './useTelefonBerechtigung';
import { WAEHL_TELEFON_SCHLUESSEL } from './useWaehlTelefon';
import { WaehlTelefonZeile } from './WaehlTelefonZeile';

function stubbe(darf = true) {
    return stubbeFetch(
        (url) => (url.pathname === '/api/telefon/berechtigung' ? antwort({ darfTelefonSehen: darf }) : undefined),
        (url) => (url.pathname === '/api/telefon/telefone' ? antwort([{ name: 'LAN: PC Büro' }, { name: 'DECT: Mobilteil Büro' }]) : undefined),
    );
}

function zeige() {
    return render(<ToastProvider><WaehlTelefonZeile /></ToastProvider>);
}

describe('WaehlTelefonZeile', () => {
    beforeEach(() => setzeTelefonBerechtigungZurueck());
    afterEach(() => {
        vi.unstubAllGlobals();
        window.localStorage.clear();
    });

    it('sagt ohne gespeichertes Telefon „noch nicht gewählt“ und bietet „Auswählen“ an', async () => {
        stubbe();
        zeige();
        const knopf = await screen.findByRole('button', { name: 'Telefon an diesem Rechner auswählen' });
        expect(knopf).toHaveTextContent('Auswählen');
        expect(screen.getByText(/Telefon an diesem Rechner:/)).toHaveTextContent('Telefon an diesem Rechner: noch nicht gewählt');
    });

    it('zeigt das gespeicherte Telefon und ändert es über die Auswahl', async () => {
        window.localStorage.setItem(WAEHL_TELEFON_SCHLUESSEL, 'LAN: PC Büro');
        stubbe();
        zeige();
        fireEvent.click(await screen.findByRole('button', { name: 'Telefon an diesem Rechner ändern' }));
        const dialog = await screen.findByRole('dialog', { name: 'Welches Telefon steht an diesem Rechner?' });
        expect(await within(dialog).findByRole('radio', { name: 'LAN: PC Büro' })).toHaveAttribute('aria-checked', 'true');
        fireEvent.click(within(dialog).getByRole('radio', { name: 'DECT: Mobilteil Büro' }));
        fireEvent.click(within(dialog).getByRole('button', { name: 'Übernehmen' }));
        await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
        expect(screen.getByText('DECT: Mobilteil Büro')).toBeInTheDocument();
        expect(window.localStorage.getItem(WAEHL_TELEFON_SCHLUESSEL)).toBe('DECT: Mobilteil Büro');
    });

    it('ist ohne Telefon-Recht nicht zu sehen', async () => {
        const fetchMock = stubbe(false);
        const { container } = zeige();
        await waitFor(() => expect(aufrufe(fetchMock, '/api/telefon/berechtigung')).toHaveLength(1));
        expect(container.textContent).not.toContain('Telefon an diesem Rechner');
    });
});
