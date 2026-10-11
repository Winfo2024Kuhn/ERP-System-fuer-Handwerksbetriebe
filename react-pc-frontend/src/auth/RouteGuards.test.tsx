import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, type AuthUser } from './AuthContext';
import { RequireAdmin, RequireAuth } from './RouteGuards';
import { antwort, stubbeFetch } from '../features/telefon/telefonTestdaten';
import { admin, benutzer } from '../features/ki/kiTestdaten';

/**
 * Die Umleitung zur Ersteinrichtung hängt allein am Backend-Flag
 * `requiresInitialSetup` (Admin + Mailserver unvollständig). Ein fehlender
 * KI-Schlüssel darf niemanden mehr dorthin zwingen.
 */

function stubbeAnmeldung(ich: AuthUser | null) {
    return stubbeFetch((url) => {
        if (url.pathname !== '/api/auth/me') return undefined;
        return ich ? antwort(ich) : antwort(null, 401);
    });
}

function Adresse() {
    const { pathname } = useLocation();
    return <span data-testid="adresse">{pathname}</span>;
}

/** Routen wie in App.tsx: /onboarding hinter RequireAdmin, der Rest hinter RequireAuth. */
function zeige(pfad: string) {
    return render(
        <MemoryRouter initialEntries={[pfad]}>
            <AuthProvider>
                <Routes>
                    <Route path="/login" element={<p>Anmeldeseite</p>} />
                    <Route path="/onboarding" element={<RequireAdmin><p>Ersteinrichtung</p></RequireAdmin>} />
                    <Route path="/projekte" element={<RequireAuth><p>Projektliste</p></RequireAuth>} />
                    <Route path="/einstellungen" element={<RequireAdmin><p>System-Einstellungen</p></RequireAdmin>} />
                </Routes>
                <Adresse />
            </AuthProvider>
        </MemoryRouter>,
    );
}

afterEach(() => vi.unstubAllGlobals());

describe('RequireAuth – Ersteinrichtung', () => {
    it('schickt einen Admin mit unvollständigem Mailserver zur Ersteinrichtung', async () => {
        stubbeAnmeldung(admin({ requiresInitialSetup: true, kiAktiv: false }));
        zeige('/projekte');

        expect(await screen.findByText('Ersteinrichtung')).toBeInTheDocument();
        expect(screen.getByTestId('adresse')).toHaveTextContent('/onboarding');
        expect(screen.queryByText('Projektliste')).not.toBeInTheDocument();
    });

    it('lässt einen Admin ohne KI-Schlüssel normal arbeiten, wenn der Mailserver steht', async () => {
        stubbeAnmeldung(admin({ requiresInitialSetup: false, kiAktiv: false }));
        zeige('/projekte');

        expect(await screen.findByText('Projektliste')).toBeInTheDocument();
        expect(screen.getByTestId('adresse')).toHaveTextContent('/projekte');
    });

    it('leitet Nicht-Admins nie zur Ersteinrichtung um', async () => {
        stubbeAnmeldung(benutzer({ requiresInitialSetup: true }));
        zeige('/projekte');

        expect(await screen.findByText('Projektliste')).toBeInTheDocument();
        expect(screen.getByTestId('adresse')).toHaveTextContent('/projekte');
    });

    it('erkennt den Admin auch nur an der Rolle', async () => {
        stubbeAnmeldung(benutzer({ roles: ['ADMIN'], admin: false, requiresInitialSetup: true }));
        zeige('/projekte');

        expect(await screen.findByText('Ersteinrichtung')).toBeInTheDocument();
    });

    it('leitet auf /onboarding selbst nicht noch einmal um (keine Schleife)', async () => {
        stubbeAnmeldung(admin({ requiresInitialSetup: true }));
        render(
            <MemoryRouter initialEntries={['/onboarding']}>
                <AuthProvider>
                    <Routes>
                        <Route path="/onboarding" element={<RequireAuth><p>Ersteinrichtung</p></RequireAuth>} />
                    </Routes>
                    <Adresse />
                </AuthProvider>
            </MemoryRouter>,
        );

        expect(await screen.findByText('Ersteinrichtung')).toBeInTheDocument();
        expect(screen.getByTestId('adresse')).toHaveTextContent('/onboarding');
    });

    it('schickt Nicht-Angemeldete zur Anmeldung', async () => {
        stubbeAnmeldung(null);
        zeige('/projekte');

        expect(await screen.findByText('Anmeldeseite')).toBeInTheDocument();
        expect(screen.getByTestId('adresse')).toHaveTextContent('/login');
    });

    it('zeigt während der Prüfung einen Ladehinweis statt der Seite', () => {
        stubbeAnmeldung(admin());
        zeige('/projekte');

        expect(screen.getByText('Anmeldung wird geprüft ...')).toBeInTheDocument();
        expect(screen.queryByText('Projektliste')).not.toBeInTheDocument();
    });
});

describe('RequireAdmin', () => {
    it('lässt Admins durch', async () => {
        stubbeAnmeldung(admin({ kiAktiv: false }));
        zeige('/einstellungen');

        expect(await screen.findByText('System-Einstellungen')).toBeInTheDocument();
    });

    it('schickt Nicht-Admins zu den Projekten', async () => {
        stubbeAnmeldung(benutzer());
        zeige('/einstellungen');

        expect(await screen.findByText('Projektliste')).toBeInTheDocument();
        expect(screen.getByTestId('adresse')).toHaveTextContent('/projekte');
    });

    it('schickt Nicht-Angemeldete zur Anmeldung', async () => {
        stubbeAnmeldung(null);
        zeige('/onboarding');

        expect(await screen.findByText('Anmeldeseite')).toBeInTheDocument();
    });
});
