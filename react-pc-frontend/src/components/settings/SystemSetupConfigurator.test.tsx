import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { SystemSetupConfigurator } from './SystemSetupConfigurator';
import { ToastProvider } from '../ui/toast';
import { ConfirmProvider } from '../ui/confirm-dialog';

interface StubOptions {
    /** Zugangsdaten des Haupt-Postfachs für die Häkchen-Prüfung (Dummy-Daten, DSGVO). */
    smtp?: { host: string; port: number; username: string; passwordSet: boolean };
}

// Die Bereiche laden beim Öffnen ihres Reiters mehrere Settings-Endpunkte.
// Wir stubben fetch URL-abhängig, damit jeder Bereich definierte Daten hat.
function stubFetch(options: StubOptions = {}) {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        const json = (body: unknown) =>
            new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
        if (url.includes('/api/settings/smtp'))
            return json(options.smtp ?? { host: '', port: 465, username: '', passwordSet: false });
        if (url.includes('/api/settings/gemini')) return json({ apiKeySet: false });
        if (url.includes('/api/settings/anfrage-funnel-spamfilter')) return json({ aktiv: true });
        if (url.includes('/api/postfaecher')) return json([{
            id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb', aktiv: true,
            sortierung: 0, hauptpostfach: true, fuerGeschaeftsdokumente: false, benutzername: 'info@musterbetrieb.example',
            passwortGesetzt: true, smtpHost: 'mail.your-server.de', smtpPort: 465, imapHost: 'mail.your-server.de',
            imapPort: 993, abrufAktiv: true, letzterAbrufAm: null, letzterAbrufFehler: null, zugewieseneBenutzer: [],
        }]);
        if (url.includes('/api/settings/datei-ordner/test'))
            return json({ success: true, message: 'Ordner gefunden und beschreibbar: C:\\Zeichnungen' });
        if (url.includes('/api/settings/datei-ordner'))
            return json({ pfad: 'C:\\Zeichnungen', networkUrl: '', konfiguriert: false });
        return json({});
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

function renderConfigurator() {
    return render(
        <ToastProvider>
            <ConfirmProvider>
                <SystemSetupConfigurator />
            </ConfirmProvider>
        </ToastProvider>
    );
}

/**
 * Der offene Reiter steht in der Adresszeile. Ohne Zurücksetzen würde ein
 * Test, der auf „Dateien" wechselt, den nächsten Test dort starten lassen.
 */
function resetHash() {
    window.history.replaceState(null, '', '/');
}

/** Wechselt auf einen Reiter und wartet, bis dessen Inhalt geladen ist. */
async function oeffneReiter(name: RegExp) {
    fireEvent.click(await screen.findByRole('tab', { name }));
}

describe('SystemSetupConfigurator – Reiter', () => {
    afterEach(() => {
        vi.unstubAllGlobals();
        resetHash();
    });

    it('startet im E-Mail-Reiter und zeigt nur dessen Inhalt', async () => {
        stubFetch();
        renderConfigurator();

        expect(await screen.findByText('info@musterbetrieb.example')).toBeInTheDocument();
        expect(screen.getByText('Postfächer')).toBeInTheDocument();
        // Der Datei-Ordner gehört in einen anderen Reiter und darf hier nicht auftauchen.
        expect(screen.queryByText(/Wo sollen Zeichnungen und Dateien liegen/i)).not.toBeInTheDocument();
    });

    it('merkt sich den offenen Reiter in der Adresszeile', async () => {
        stubFetch();
        renderConfigurator();

        await oeffneReiter(/KI-Funktionen/i);

        expect(await screen.findByText(/Anfragen von der Webseite/i)).toBeInTheDocument();
        expect(window.location.hash).toBe('#ki');
    });
});

describe('SystemSetupConfigurator – E-Mail', () => {
    afterEach(() => {
        vi.unstubAllGlobals();
        resetHash();
    });

    it('nutzt für die Postfächer nur noch /api/postfaecher, nicht die alten Einzel-Endpunkte', async () => {
        const fetchMock = stubFetch();
        renderConfigurator();
        await screen.findByText('info@musterbetrieb.example');
        const urls = fetchMock.mock.calls.map(([url]) => String(url));
        expect(urls).toContain('/api/postfaecher');
        expect(urls.some(url => /mail-from|dokument-mail|settings\/imap|email-account/.test(url))).toBe(false);
    });
});

describe('SystemSetupConfigurator – Gemeinsamer Datei-Ordner', () => {
    afterEach(() => {
        vi.unstubAllGlobals();
        resetHash();
    });

    it('zeigt den Bereich mit vorbelegtem Pfad', async () => {
        stubFetch();
        renderConfigurator();
        await oeffneReiter(/Dateien/i);

        expect(await screen.findByText(/Wo sollen Zeichnungen und Dateien liegen/i)).toBeInTheDocument();
        await waitFor(() =>
            expect(screen.getByPlaceholderText('C:\\Zeichnungen')).toHaveValue('C:\\Zeichnungen'));
    });

    it('Prüfen ruft den Test-Endpunkt auf und zeigt das Ergebnis', async () => {
        const fetchMock = stubFetch();
        renderConfigurator();
        await oeffneReiter(/Dateien/i);

        const pruefenButton = await screen.findByRole('button', { name: /Ordner prüfen/i });
        await waitFor(() => expect(pruefenButton).not.toBeDisabled());
        fireEvent.click(pruefenButton);
        await waitFor(() =>
            expect(fetchMock).toHaveBeenCalledWith(
                '/api/settings/datei-ordner/test',
                expect.objectContaining({ method: 'POST' })
            ));
        expect(await screen.findByText(/gefunden und beschreibbar/i)).toBeInTheDocument();
    });
});

describe('SystemSetupConfigurator – Telefon', () => {
    afterEach(() => {
        vi.unstubAllGlobals();
        resetHash();
    });

    it('hat einen eigenen Reiter "Telefon" für die FRITZ!Box', async () => {
        stubFetch();
        renderConfigurator();
        await oeffneReiter(/Telefon/);
        expect(await screen.findByText('Telefon (FRITZ!Box)')).toBeInTheDocument();
        expect(window.location.hash).toBe('#telefon');
    });
});
