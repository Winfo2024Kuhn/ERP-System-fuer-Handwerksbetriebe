import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../auth/AuthContext';
import { ToastProvider } from '../ui/toast';
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

function zeige(pfad = '/emails/inbox') {
    return render(
        <MemoryRouter initialEntries={[pfad]}>
            <ToastProvider><AuthProvider><RibbonNavigation /></AuthProvider></ToastProvider>
        </MemoryRouter>,
    );
}

describe('Menüleiste – E-Mail-Einstellungen', () => {
    beforeEach(() => setzeTelefonBerechtigungZurueck());
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt E-Mail-Einstellungen als dritten E-Mail-Eintrag – auch ohne Admin-Recht', async () => {
        stubbe(false);
        zeige();
        fireEvent.click(await screen.findByRole('button', { name: 'Kommunikation' }));

        const link = await screen.findByRole('link', { name: /E-Mail-Einstellungen/ });
        expect(link).toHaveAttribute('href', '/email-einstellungen');
        const emailLinks = screen.getAllByRole('link').filter((l) => /E-Mail|Mailtexte/.test(l.textContent ?? ''));
        expect(emailLinks.map((l) => l.getAttribute('href'))).toEqual(['/emails/inbox', '/email-textvorlagen', '/email-einstellungen']);
    });

    it('auf der Seite selbst ist der Eintrag unter Kommunikation markiert', async () => {
        stubbe(false);
        zeige('/email-einstellungen');
        const link = await screen.findByRole('link', { name: /E-Mail-Einstellungen/ });
        expect(link).toHaveClass('ring-1');
        expect(screen.getByRole('link', { name: /Mailtexte für Dokumente/ })).not.toHaveClass('ring-1');
    });
});
