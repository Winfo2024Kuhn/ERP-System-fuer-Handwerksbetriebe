import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../auth/AuthContext';
import { ToastProvider } from '../ui/toast';
import { RibbonNavigation } from './RibbonNav';
import { MobileBottomNav } from './MobileBottomNav';
import { antwort, aufrufe, stubbeFetch } from '../../features/telefon/telefonTestdaten';
import { setzeTelefonBerechtigungZurueck } from '../../features/telefon/useTelefonBerechtigung';
import { setzeZeiterfassungSichtbarkeitZurueck } from '../../features/zeiterfassung/useZeiterfassungSichtbarkeit';

/** Dummy-Daten (DSGVO): Max Mustermann aus der Werkstatt bzw. dem Büro. */
function stubbe(darf: boolean) {
    return stubbeFetch(
        (url) => (url.pathname === '/api/auth/me'
            ? antwort({ id: 1, displayName: 'Max Mustermann', username: 'max', active: true, roles: [], admin: false, requiresInitialSetup: false })
            : undefined),
        (url) => (url.pathname === '/api/zeitverwaltung/sichtbarkeit' ? antwort({ darfZeiterfassungSehen: darf }) : undefined),
        (url) => (url.pathname === '/api/telefon/berechtigung' ? antwort({ darfTelefonSehen: false }) : undefined),
        (url) => (url.pathname === '/api/notifications/summary' ? antwort({ totalCount: 0, categories: [], recentItems: [] }) : undefined),
    );
}

function zeige(inhalt: React.ReactNode) {
    return render(
        <MemoryRouter initialEntries={['/projekte']}>
            <ToastProvider><AuthProvider>{inhalt}</AuthProvider></ToastProvider>
        </MemoryRouter>,
    );
}

async function oeffneMehr() {
    const mehr = await screen.findByRole('button', { name: /Mehr/ });
    // Die Navigation schließt beim Start einmal alle Blätter (setTimeout 0) – das abwarten.
    await new Promise((r) => setTimeout(r, 10));
    fireEvent.click(mehr);
    const blatt = await screen.findByRole('heading', { name: 'Weitere Bereiche' });
    return blatt.closest('div.fixed') as HTMLElement;
}

describe('Menü – Bereich „Zeiterfassung“', () => {
    beforeEach(() => { setzeTelefonBerechtigungZurueck(); setzeZeiterfassungSichtbarkeitZurueck(); });
    afterEach(() => vi.unstubAllGlobals());

    it('Menüleiste: zeigt mit Recht den Reiter „Zeiterfassung“ samt Seiten', async () => {
        stubbe(true);
        zeige(<RibbonNavigation />);
        fireEvent.click(await screen.findByRole('button', { name: 'Zeiterfassung' }));
        expect(await screen.findByRole('link', { name: /Monatsabschluss/ })).toHaveAttribute('href', '/monatsabschluss');
        expect(screen.getByRole('link', { name: /Urlaubsanträge/ })).toHaveAttribute('href', '/urlaubsantraege');
    });

    it('Menüleiste: blendet den Reiter ohne Recht aus – die übrigen Reiter bleiben', async () => {
        const fetchMock = stubbe(false);
        zeige(<RibbonNavigation />);
        await waitFor(() => expect(aufrufe(fetchMock, '/api/zeitverwaltung/sichtbarkeit')).toHaveLength(1));
        expect(await screen.findByRole('button', { name: 'Kommunikation' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Vorlagen & Stammdaten/ })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Zeiterfassung' })).toBeNull();
    });

    it('Menüleiste: zeigt den Reiter nicht, solange das Recht noch geprüft wird', async () => {
        stubbeFetch(
            (url) => (url.pathname === '/api/zeitverwaltung/sichtbarkeit' ? new Promise<Response>(() => undefined) as unknown as Response : undefined),
            (url) => (url.pathname === '/api/notifications/summary' ? antwort({ totalCount: 0, categories: [], recentItems: [] }) : undefined),
        );
        zeige(<RibbonNavigation />);
        expect(await screen.findByRole('button', { name: 'Kommunikation' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Zeiterfassung' })).toBeNull();
    });

    it('Handy-Navigation: zeigt mit Recht den Reiter „Zeit“ und die Feiertage unter „Mehr“', async () => {
        stubbe(true);
        zeige(<MobileBottomNav />);
        expect(await screen.findByRole('link', { name: /Zeit/ })).toHaveAttribute('href', '/zeitbuchungen');
        const bereich = await oeffneMehr();
        expect(within(bereich).getByRole('link', { name: /Feiertage/ })).toBeInTheDocument();
    });

    it('Handy-Navigation: blendet ohne Recht den Reiter „Zeit“ und die Feiertage aus', async () => {
        const fetchMock = stubbe(false);
        zeige(<MobileBottomNav />);
        await waitFor(() => expect(aufrufe(fetchMock, '/api/zeitverwaltung/sichtbarkeit')).toHaveLength(1));
        expect(await screen.findByRole('link', { name: /Projekte/ })).toBeInTheDocument();
        await waitFor(() => expect(screen.queryByRole('link', { name: /Zeit/ })).toBeNull());
        const bereich = await oeffneMehr();
        expect(within(bereich).queryByRole('link', { name: /Feiertage/ })).toBeNull();
        expect(within(bereich).getByRole('link', { name: /Leistungen/ })).toBeInTheDocument();
    });
});
