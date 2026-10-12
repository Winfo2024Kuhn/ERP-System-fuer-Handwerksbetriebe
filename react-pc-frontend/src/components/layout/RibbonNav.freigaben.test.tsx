import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../auth/AuthContext';
import { ToastProvider } from '../ui/toast';
import { RibbonNavigation } from './RibbonNav';
import { antwort, stubbeFetch } from '../../features/telefon/telefonTestdaten';
import { setzeTelefonBerechtigungZurueck } from '../../features/telefon/useTelefonBerechtigung';
import { admin } from '../../features/ki/kiTestdaten';

describe('Menüleiste – noch nicht freigegebene Seiten', () => {
    beforeEach(() => setzeTelefonBerechtigungZurueck());
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt unter Einkauf nur „Bestellungen“, nicht den „Bedarf“', async () => {
        stubbeFetch(
            (url) => (url.pathname === '/api/auth/me' ? antwort(admin({ kiAktiv: false })) : undefined),
            (url) => (url.pathname === '/api/telefon/berechtigung' ? antwort({ darfTelefonSehen: false }) : undefined),
            (url) => (url.pathname === '/api/notifications/summary' ? antwort({ totalCount: 0, categories: [], recentItems: [] }) : undefined),
        );
        render(
            <MemoryRouter initialEntries={['/bestellungen']}>
                <ToastProvider><AuthProvider><RibbonNavigation /></AuthProvider></ToastProvider>
            </MemoryRouter>,
        );

        // Erst wenn der Name in der Leiste steht, ist die Anmeldung geladen.
        expect(await screen.findByText('Max Mustermann')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: /Bestellungen/ })).toHaveAttribute('href', '/bestellungen');
        expect(screen.queryByRole('link', { name: /Bedarf/ })).not.toBeInTheDocument();
    });
});
