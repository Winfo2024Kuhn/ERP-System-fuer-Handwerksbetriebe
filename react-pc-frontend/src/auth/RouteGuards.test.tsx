import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ANMELDUNG_TIMEOUT_MS, AuthProvider, type AuthUser } from './AuthContext';
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

afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
});

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

        expect(screen.getByText('Anmeldung wird geprüft …')).toBeInTheDocument();
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

describe('Langsame oder fehlende Verbindung beim Start', () => {
    /** /api/auth/me hängt, bis die Anfrage abgebrochen wird – wie ein Funkloch unterwegs. */
    function stubbeHaengendeAnmeldung() {
        vi.stubGlobal('fetch', vi.fn((_url: RequestInfo | URL, init?: RequestInit) => new Promise<Response>((_, reject) => {
            init?.signal?.addEventListener('abort', () => reject(new DOMException('Abgebrochen', 'AbortError')));
        })));
    }

    it('zeigt nach 8 Sekunden einen Hinweis auf die langsame Verbindung', async () => {
        vi.useFakeTimers();
        stubbeHaengendeAnmeldung();
        zeige('/projekte');

        expect(screen.queryByText(/Verbindung ist gerade langsam/)).not.toBeInTheDocument();
        await act(() => vi.advanceTimersByTimeAsync(8000));

        expect(screen.getByText(/Verbindung ist gerade langsam/)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Erneut versuchen/ })).toBeInTheDocument();
    });

    it('bricht die Anmeldeprüfung ab und schickt niemanden zur Anmeldeseite', async () => {
        vi.useFakeTimers();
        stubbeHaengendeAnmeldung();
        zeige('/projekte');

        await act(() => vi.advanceTimersByTimeAsync(ANMELDUNG_TIMEOUT_MS));

        expect(screen.getByRole('alert')).toHaveTextContent('Der Server antwortet gerade nicht');
        expect(screen.getByTestId('adresse')).toHaveTextContent('/projekte');
        expect(screen.queryByText('Anmeldeseite')).not.toBeInTheDocument();
    });

    it('prüft per „Erneut versuchen“ noch einmal und öffnet dann die Seite', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch'); }));
        zeige('/projekte');
        const knopf = await screen.findByRole('button', { name: /Erneut versuchen/ });

        stubbeAnmeldung(admin({ requiresInitialSetup: false }));
        await userEvent.click(knopf);

        expect(await screen.findByText('Projektliste')).toBeInTheDocument();
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('schickt zur Anmeldung, wenn der Server antwortet, aber kein JSON liefert', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => new Response('<html>Fehler</html>', { status: 200, headers: { 'Content-Type': 'text/html' } })));
        zeige('/projekte');

        expect(await screen.findByText('Anmeldeseite')).toBeInTheDocument();
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('lässt auch Admin-Seiten bei fehlender Verbindung stehen', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch'); }));
        zeige('/einstellungen');

        expect(await screen.findByRole('alert')).toHaveTextContent('Der Server antwortet gerade nicht');
        expect(screen.getByTestId('adresse')).toHaveTextContent('/einstellungen');
    });
});
