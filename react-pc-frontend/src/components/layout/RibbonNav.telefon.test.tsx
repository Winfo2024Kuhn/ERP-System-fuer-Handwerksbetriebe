import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../auth/AuthContext';
import { RibbonNavigation } from './RibbonNav';
import { antwort, stubbeFetch } from '../../features/telefon/telefonTestdaten';
import { setzeTelefonBerechtigungZurueck } from '../../features/telefon/useTelefonBerechtigung';

function stubbe(darf: boolean) {
    return stubbeFetch(
        (url) => (url.pathname === '/api/auth/me'
            ? antwort({ id: 1, displayName: 'Max Mustermann', username: 'max', active: true, roles: [], admin: false, requiresInitialSetup: false })
            : undefined),
        (url) => (url.pathname === '/api/telefon/berechtigung' ? antwort({ darfTelefonSehen: darf }) : undefined),
        (url) => (url.pathname === '/api/telefon/sprachnachrichten/anzahl-neu' ? antwort({ anzahl: 3 }) : undefined),
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

describe('Menüleiste – Telefon', () => {
    beforeEach(() => setzeTelefonBerechtigungZurueck());
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt mit Recht Anrufe und Anrufbeantworter mit Zähler unter Kommunikation', async () => {
        stubbe(true);
        zeige();
        fireEvent.click(screen.getByRole('button', { name: 'Kommunikation' }));
        expect(await screen.findByRole('link', { name: /Anrufe/ })).toHaveAttribute('href', '/telefon/anrufe');
        const ab = screen.getByRole('link', { name: /Anrufbeantworter/ });
        expect(ab).toHaveAttribute('href', '/telefon/anrufbeantworter');
        await waitFor(() => expect(ab).toHaveTextContent('3'));
        expect(screen.getByText('Telefon')).toBeInTheDocument();
    });

    it('zeigt ohne Recht keine Telefon-Einträge und fragt keine Zähler ab', async () => {
        const fetchMock = stubbe(false);
        zeige();
        await waitFor(() => expect(fetchMock.mock.calls.some(([u]) => String(u).includes('/api/telefon/berechtigung'))).toBe(true));
        fireEvent.click(screen.getByRole('button', { name: 'Kommunikation' }));
        expect(screen.getByRole('link', { name: /E-Mail Center/ })).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: /Anrufe/ })).toBeNull();
        expect(fetchMock.mock.calls.some(([u]) => String(u).includes('anzahl-neu'))).toBe(false);
    });
});
