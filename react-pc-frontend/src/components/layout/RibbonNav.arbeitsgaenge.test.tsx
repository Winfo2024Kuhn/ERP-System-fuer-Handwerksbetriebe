import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../auth/AuthContext';
import { ToastProvider } from '../ui/toast';
import { RibbonNavigation } from './RibbonNav';
import { antwort, stubbeFetch } from '../../features/telefon/telefonTestdaten';
import { setzeTelefonBerechtigungZurueck } from '../../features/telefon/useTelefonBerechtigung';

/** Arbeitsgänge (und damit die Abteilungs-Auswahl dort) pflegen nur noch Admins. Nur Dummy-Daten. */
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
        <MemoryRouter initialEntries={['/projekte']}>
            <ToastProvider><AuthProvider><RibbonNavigation /></AuthProvider></ToastProvider>
        </MemoryRouter>,
    );
}

describe('Menüleiste – Arbeitsgänge nur für Admins', () => {
    beforeEach(() => setzeTelefonBerechtigungZurueck());
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt Arbeitsgänge für Admins unter Vorlagen & Stammdaten → Administration', async () => {
        stubbe(true);
        zeige();
        fireEvent.click(await screen.findByRole('button', { name: 'Vorlagen & Stammdaten' }));

        expect(await screen.findByRole('link', { name: /Arbeitsgänge/ })).toHaveAttribute('href', '/arbeitsgaenge');
    });

    it('blendet Arbeitsgänge für andere Benutzer aus, die übrigen Stammdaten bleiben', async () => {
        stubbe(false);
        zeige();
        fireEvent.click(await screen.findByRole('button', { name: 'Vorlagen & Stammdaten' }));

        expect(await screen.findByRole('link', { name: /Artikel/ })).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: /Arbeitsgänge/ })).toBeNull();
    });
});
