import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../auth/AuthContext';
import { RibbonNavigation } from './RibbonNav';
import { antwort, stubbeFetch } from '../../features/telefon/telefonTestdaten';
import { setzeTelefonBerechtigungZurueck } from '../../features/telefon/useTelefonBerechtigung';

function stubbe(admin: boolean) {
    return stubbeFetch(
        (url) => (url.pathname === '/api/auth/me'
            ? antwort({ id: 1, displayName: 'Max Mustermann', username: 'max', active: true, roles: [], admin, requiresInitialSetup: false })
            : undefined),
        (url) => (url.pathname === '/api/telefon/berechtigung' ? antwort({ darfTelefonSehen: false }) : undefined),
        (url) => (url.pathname === '/api/notifications/summary' ? antwort({ totalCount: 0, categories: [], recentItems: [] }) : undefined),
    );
}

function zeige() {
    return render(
        <MemoryRouter initialEntries={['/emails/inbox']}>
            <AuthProvider><RibbonNavigation /></AuthProvider>
        </MemoryRouter>,
    );
}

describe('Menüleiste – Website', () => {
    beforeEach(() => setzeTelefonBerechtigungZurueck());
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt Neuigkeiten für Admins unter Kommunikation', async () => {
        stubbe(true);
        zeige();
        fireEvent.click(await screen.findByRole('button', { name: 'Kommunikation' }));

        expect(await screen.findByRole('link', { name: /Neuigkeiten/ })).toHaveAttribute('href', '/website');
    });

    it('zeigt Neuigkeiten nicht mehr unter Finanzen & Controlling', async () => {
        stubbe(true);
        zeige();
        fireEvent.click(await screen.findByRole('button', { name: 'Finanzen & Controlling' }));

        expect(await screen.findByRole('link', { name: /Erfolgsanalyse/ })).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: /Neuigkeiten/ })).toBeNull();
    });
});
