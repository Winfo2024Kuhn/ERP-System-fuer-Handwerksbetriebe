import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from '../../auth/AuthContext';
import { ToastProvider } from '../ui/toast';
import { RibbonNavigation } from './RibbonNav';
import { MobileBottomNav } from './MobileBottomNav';
import { antwort, stubbeFetch } from '../../features/telefon/telefonTestdaten';
import { setzeTelefonBerechtigungZurueck } from '../../features/telefon/useTelefonBerechtigung';

/**
 * „Benutzer verwalten“ ist nur für Admins da – in der Menüleiste (Benutzermenü)
 * und in der Handy-Navigation („Mehr“ › Einstellungen › Benutzer). Dasselbe gilt für
 * „Formularwesen“ (Menüleiste › Vorlagen & Stammdaten › Administration).
 * Nur Dummy-Daten (DSGVO).
 */

function stubbe(admin: boolean) {
    return stubbeFetch(
        (url) => (url.pathname === '/api/auth/me'
            ? antwort({ id: 1, displayName: 'Max Mustermann', username: 'max', active: true, roles: admin ? ['ADMIN'] : ['USER'], admin, requiresInitialSetup: false })
            : undefined),
        (url) => (url.pathname === '/api/telefon/berechtigung' ? antwort({ darfTelefonSehen: false }) : undefined),
        (url) => (url.pathname === '/api/notifications/summary' ? antwort({ totalCount: 0, categories: [], recentItems: [] }) : undefined),
    );
}

/** Wie RequireAuth in der App: Navigation erst nach geprüfter Anmeldung. */
function NachAnmeldung({ children }: { children: React.ReactNode }) {
    const { loading } = useAuth();
    return loading ? null : <>{children}</>;
}

function zeige(inhalt: React.ReactNode) {
    return render(
        <MemoryRouter initialEntries={['/projekte']}>
            <ToastProvider><AuthProvider><NachAnmeldung>{inhalt}</NachAnmeldung></AuthProvider></ToastProvider>
        </MemoryRouter>,
    );
}

async function oeffneBenutzermenue() {
    fireEvent.click(await screen.findByRole('button', { name: /Max Mustermann/ }));
    return screen.findByRole('button', { name: 'Abmelden' });
}

async function oeffneMehr() {
    const mehr = await screen.findByRole('button', { name: /Mehr/ });
    // Die Navigation schließt beim Start einmal alle Blätter (setTimeout 0) – das abwarten.
    await new Promise((r) => setTimeout(r, 10));
    fireEvent.click(mehr);
    const blatt = await screen.findByRole('heading', { name: 'Weitere Bereiche' });
    return blatt.closest('div.fixed') as HTMLElement;
}

describe('„Benutzer verwalten“ nur für Admins', () => {
    beforeEach(() => setzeTelefonBerechtigungZurueck());
    afterEach(() => vi.unstubAllGlobals());

    it('Menüleiste: Admin sieht „Benutzer verwalten“', async () => {
        stubbe(true);
        zeige(<RibbonNavigation />);
        await oeffneBenutzermenue();
        expect(screen.getByRole('link', { name: /Benutzer verwalten/ })).toHaveAttribute('href', '/benutzer');
    });

    it('Menüleiste: normaler Benutzer sieht „Benutzer verwalten“ nicht', async () => {
        stubbe(false);
        zeige(<RibbonNavigation />);
        await oeffneBenutzermenue();
        expect(screen.getByText('Angemeldet')).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: /Benutzer verwalten/ })).toBeNull();
        expect(screen.queryByRole('link', { name: /Benutzer/ })).toBeNull();
    });

    it('Handy-Navigation: Admin sieht „Benutzer“ unter „Mehr“', async () => {
        stubbe(true);
        zeige(<MobileBottomNav />);
        const bereich = await oeffneMehr();
        expect(within(bereich).getByRole('link', { name: /Benutzer/ })).toHaveAttribute('href', '/benutzer');
    });

    it('Handy-Navigation: normaler Benutzer sieht „Benutzer“ nicht', async () => {
        stubbe(false);
        zeige(<MobileBottomNav />);
        const bereich = await oeffneMehr();
        expect(within(bereich).getByRole('link', { name: /Feiertage/ })).toBeInTheDocument();
        expect(within(bereich).queryByRole('link', { name: /Benutzer/ })).toBeNull();
    });
});

describe('„Formularwesen“ im Admin-Bereich', () => {
    beforeEach(() => setzeTelefonBerechtigungZurueck());
    afterEach(() => vi.unstubAllGlobals());

    async function oeffneStammdaten() {
        fireEvent.click(await screen.findByRole('button', { name: /Vorlagen & Stammdaten/ }));
        return screen.findByRole('link', { name: /Textvorlagen/ });
    }

    it('Menüleiste: Admin findet „Formularwesen“ unter Administration neben „Firma“', async () => {
        stubbe(true);
        zeige(<RibbonNavigation />);
        await oeffneStammdaten();
        const formularwesen = screen.getByRole('link', { name: /Formularwesen/ });
        expect(formularwesen).toHaveAttribute('href', '/formulare');
        const gruppe = screen.getByText('Administration').parentElement as HTMLElement;
        expect(within(gruppe).getByRole('link', { name: /Formularwesen/ })).toBeInTheDocument();
        expect(within(gruppe).getByRole('link', { name: /Firma/ })).toBeInTheDocument();
    });

    it('Menüleiste: normaler Benutzer sieht „Formularwesen“ nicht', async () => {
        stubbe(false);
        zeige(<RibbonNavigation />);
        await oeffneStammdaten();
        expect(screen.queryByRole('link', { name: /Formularwesen/ })).toBeNull();
    });

    it('Handy-Navigation: nur Admins sehen „Formularwesen“', async () => {
        stubbe(false);
        const { unmount } = zeige(<MobileBottomNav />);
        let bereich = await oeffneMehr();
        expect(within(bereich).queryByRole('link', { name: /Formularwesen/ })).toBeNull();
        unmount();

        stubbe(true);
        zeige(<MobileBottomNav />);
        bereich = await oeffneMehr();
        expect(within(bereich).getByRole('link', { name: /Formularwesen/ })).toHaveAttribute('href', '/formulare');
    });
});
