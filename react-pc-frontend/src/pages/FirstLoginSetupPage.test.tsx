import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import { ToastProvider } from '../components/ui/toast';
import FirstLoginSetupPage from './FirstLoginSetupPage';
import { antwort, aufrufe, stubbeFetch } from '../features/telefon/telefonTestdaten';
import { admin } from '../features/ki/kiTestdaten';

// Die Reiter laden und speichern selbst und haben eigene Tests. Hier geht es
// nur um den Abschluss der Ersteinrichtung.
vi.mock('../components/settings/SystemSetupConfigurator', () => ({
    SystemSetupConfigurator: () => <div>Einstellungs-Reiter</div>,
}));

const SMTP_VOLLSTAENDIG = { host: 'mail.musterbetrieb.example', port: 465, username: 'info@musterbetrieb.example', passwordSet: true };
const SMTP_LEER = { host: '', port: 465, username: '', passwordSet: false };

/**
 * @param smtp Was der Mailserver-Bereich gespeichert hat.
 * @param nachPruefung `requiresInitialSetup`, das das Backend beim Neuladen der Anmeldung meldet.
 */
function stubbe(smtp: object, nachPruefung = false) {
    let meAufrufe = 0;
    return stubbeFetch(
        (url) => {
            if (url.pathname !== '/api/auth/me') return undefined;
            meAufrufe += 1;
            // Beim Laden fehlt der Mailserver noch; kein KI-Schlüssel, und das ist in Ordnung.
            return antwort(admin({ displayName: 'Erika Mustermann', kiAktiv: false, requiresInitialSetup: meAufrufe === 1 ? true : nachPruefung }));
        },
        (url) => (url.pathname === '/api/settings/smtp' ? antwort(smtp) : undefined),
        (url) => (url.pathname === '/api/settings/gemini' ? antwort({ apiKeySet: false }) : undefined),
    );
}

async function zeige() {
    render(
        <MemoryRouter initialEntries={['/onboarding']}>
            <ToastProvider>
                <AuthProvider>
                    <Routes>
                        <Route path="/onboarding" element={<FirstLoginSetupPage />} />
                        <Route path="/projekte" element={<p>Projektliste</p>} />
                    </Routes>
                </AuthProvider>
            </ToastProvider>
        </MemoryRouter>,
    );
    await screen.findByText(/Hallo Erika Mustermann/);
}

afterEach(() => vi.unstubAllGlobals());

describe('Ersteinrichtung', () => {
    it('verlangt nur noch den E-Mail-Server und nennt die KI freiwillig', async () => {
        stubbe(SMTP_LEER);
        await zeige();

        expect(screen.getByText(/bitte richten Sie jetzt den E-Mail-Server ein/)).toHaveTextContent(
            'KI-Funktionen sind freiwillig – ohne Schlüssel bleiben sie aus, alles andere funktioniert.',
        );
        expect(screen.getByText(/Sobald der E-Mail-Server vollständig gespeichert ist/)).toBeInTheDocument();
        expect(screen.queryByText(/Gemini API und E-Mail-Server/)).not.toBeInTheDocument();
    });

    it('schließt mit vollständigem E-Mail-Server ab – ganz ohne KI-Schlüssel', async () => {
        const mock = stubbe(SMTP_VOLLSTAENDIG);
        const user = userEvent.setup();
        await zeige();

        await user.click(screen.getByRole('button', { name: /Einrichtung abschließen/ }));

        expect(await screen.findByText('Projektliste')).toBeInTheDocument();
        expect(screen.getByText('Ersteinrichtung abgeschlossen.')).toBeInTheDocument();
        // Der KI-Schlüssel wird für den Abschluss gar nicht mehr abgefragt.
        expect(aufrufe(mock, '/api/settings/gemini')).toHaveLength(0);
        expect(aufrufe(mock, '/api/auth/me')).toHaveLength(2);
    });

    it('meldet einen unvollständigen E-Mail-Server und bleibt auf der Seite', async () => {
        const mock = stubbe(SMTP_LEER);
        const user = userEvent.setup();
        await zeige();

        await user.click(screen.getByRole('button', { name: /Einrichtung abschließen/ }));

        expect(await screen.findByText('E-Mail-Server unvollständig: bitte Server, Benutzername und Passwort speichern.')).toBeInTheDocument();
        expect(screen.queryByText('Projektliste')).not.toBeInTheDocument();
        expect(aufrufe(mock, '/api/auth/me')).toHaveLength(1);
        await waitFor(() => expect(screen.getByRole('button', { name: /Einrichtung abschließen/ })).toBeEnabled());
    });

    it('bleibt stehen, wenn das Backend die Einrichtung noch nicht als fertig meldet', async () => {
        stubbe(SMTP_VOLLSTAENDIG, true);
        const user = userEvent.setup();
        await zeige();

        await user.click(screen.getByRole('button', { name: /Einrichtung abschließen/ }));

        expect(await screen.findByText('Einrichtung noch nicht fertig. Bitte den E-Mail-Server vollständig speichern.')).toBeInTheDocument();
        expect(screen.queryByText('Projektliste')).not.toBeInTheDocument();
    });

    it('meldet einen Verbindungsfehler als Toast', async () => {
        stubbe(SMTP_VOLLSTAENDIG);
        const user = userEvent.setup();
        await zeige();
        vi.mocked(fetch).mockRejectedValueOnce(new TypeError('Failed to fetch'));

        await user.click(screen.getByRole('button', { name: /Einrichtung abschließen/ }));

        expect(await screen.findByText('Konfiguration konnte nicht geprüft werden. Bitte später erneut versuchen.')).toBeInTheDocument();
    });
});
