import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../auth/AuthContext';
import { ToastProvider } from '../ui/toast';
import { RibbonNavigation } from './RibbonNav';
import { antwort, stubbeFetch } from '../../features/telefon/telefonTestdaten';
import { setzeTelefonBerechtigungZurueck } from '../../features/telefon/useTelefonBerechtigung';
import { admin } from '../../features/ki/kiTestdaten';

function stubbe(kiAktiv: boolean) {
    return stubbeFetch(
        (url) => (url.pathname === '/api/auth/me' ? antwort(admin({ kiAktiv })) : undefined),
        (url) => (url.pathname === '/api/telefon/berechtigung' ? antwort({ darfTelefonSehen: false }) : undefined),
        (url) => (url.pathname === '/api/notifications/summary' ? antwort({ totalCount: 0, categories: [], recentItems: [] }) : undefined),
    );
}

function zeige() {
    return render(
        <MemoryRouter initialEntries={['/projekte']}>
            <ToastProvider><AuthProvider><RibbonNavigation /></AuthProvider></ToastProvider>
        </MemoryRouter>,
    );
}

describe('Menüleiste – KI-Hilfe', () => {
    beforeEach(() => setzeTelefonBerechtigungZurueck());
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt ohne KI-Schlüssel gar keinen KI-Hilfe-Knopf', async () => {
        stubbe(false);
        zeige();

        // Erst wenn der Name in der Leiste steht, ist die Anmeldung geladen.
        expect(await screen.findByText('Max Mustermann')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'KI-Hilfe' })).not.toBeInTheDocument();
        expect(screen.queryByTitle('KI-Hilfe öffnen')).not.toBeInTheDocument();
    });

    it('zeigt mit KI-Schlüssel den KI-Hilfe-Knopf, der den Chat öffnet', async () => {
        stubbe(true);
        const geoeffnet = vi.fn();
        window.addEventListener('ki-hilfe-open', geoeffnet);
        zeige();

        const knopf = await screen.findByRole('button', { name: 'KI-Hilfe' });
        knopf.click();
        expect(geoeffnet).toHaveBeenCalledTimes(1);
        window.removeEventListener('ki-hilfe-open', geoeffnet);
    });
});
