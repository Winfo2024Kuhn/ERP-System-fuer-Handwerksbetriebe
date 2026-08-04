import { describe, expect, it, vi, afterEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { SystemSetupConfigurator } from './SystemSetupConfigurator';
import { ToastProvider } from '../ui/toast';

// Der Configurator lädt beim Mount mehrere Settings-Endpunkte.
// Wir stubben fetch URL-abhängig, damit jeder Bereich definierte Daten hat.
function stubFetch() {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        const json = (body: unknown) =>
            new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
        if (url.includes('/api/settings/smtp')) return json({ host: '', port: 465, username: '', passwordSet: false });
        if (url.includes('/api/settings/imap')) return json({ host: '', port: 993, username: '', passwordSet: false });
        if (url.includes('/api/settings/gemini')) return json({ apiKeySet: false });
        if (url.includes('/api/settings/anfrage-funnel-spamfilter')) return json({ aktiv: true });
        if (url.includes('/api/settings/mail-from')) return json({ address: '', smtpUsername: '' });
        // Reihenfolge wichtig: der Test-Endpunkt enthält den Basispfad als Präfix.
        if (url.includes('/api/settings/dokument-mail/test'))
            return json({ success: true, message: 'Verbindung zum Postfach steht.' });
        if (url.includes('/api/settings/dokument-mail'))
            return json({ aktiv: false, host: '', port: 465, username: '', passwordSet: false, fromAddress: '' });
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
            <SystemSetupConfigurator />
        </ToastProvider>
    );
}

describe('SystemSetupConfigurator – Gemeinsamer Datei-Ordner', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt den Bereich mit vorbelegtem Pfad', async () => {
        stubFetch();
        renderConfigurator();
        expect(await screen.findByText(/Wo sollen Zeichnungen und Dateien liegen/i)).toBeInTheDocument();
        await waitFor(() =>
            expect(screen.getByPlaceholderText('C:\\Zeichnungen')).toHaveValue('C:\\Zeichnungen'));
    });

    it('Prüfen ruft den Test-Endpunkt auf und zeigt das Ergebnis', async () => {
        const fetchMock = stubFetch();
        renderConfigurator();
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

describe('SystemSetupConfigurator – Postfach für Rechnungen und Mahnungen', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt den Bereich, hält die Felder aber zu solange er ausgeschaltet ist', async () => {
        stubFetch();
        renderConfigurator();

        expect(await screen.findByText(/Postfach für Rechnungen und Mahnungen/i)).toBeInTheDocument();
        // Ausgeschaltet: keine Server-Felder, damit die Seite nicht überladen wirkt.
        expect(screen.queryByLabelText(/Mail-Server für den Versand/i)).not.toBeInTheDocument();
    });

    it('blendet die Felder ein, sobald das eigene Postfach eingeschaltet wird', async () => {
        stubFetch();
        renderConfigurator();

        const schalter = await screen.findByLabelText(/Eigenes Postfach für diese Dokumente verwenden/i);
        fireEvent.click(schalter);

        expect(await screen.findByLabelText(/Mail-Server für den Versand/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/E-Mail-Adresse des Postfachs/i)).toBeInTheDocument();
    });

    it('Verbindung testen ruft den Test-Endpunkt auf und zeigt das Ergebnis', async () => {
        const fetchMock = stubFetch();
        renderConfigurator();

        const schalter = await screen.findByLabelText(/Eigenes Postfach für diese Dokumente verwenden/i);
        fireEvent.click(schalter);

        const serverFeld = await screen.findByLabelText(/Mail-Server für den Versand/i);
        fireEvent.change(serverFeld, { target: { value: 'mail.musterfirma-beispiel.de' } });

        const testButton = screen.getByRole('button', { name: /Verbindung testen/i });
        await waitFor(() => expect(testButton).not.toBeDisabled());
        fireEvent.click(testButton);

        await waitFor(() =>
            expect(fetchMock).toHaveBeenCalledWith(
                '/api/settings/dokument-mail/test',
                expect.objectContaining({ method: 'POST' })
            ));
        expect(await screen.findByText(/Verbindung zum Postfach steht/i)).toBeInTheDocument();
    });

    it('speichert über den PUT-Endpunkt', async () => {
        const fetchMock = stubFetch();
        renderConfigurator();

        const speichern = await screen.findByRole('button', { name: /Postfach speichern/i });
        fireEvent.click(speichern);

        await waitFor(() =>
            expect(fetchMock).toHaveBeenCalledWith(
                '/api/settings/dokument-mail',
                expect.objectContaining({ method: 'PUT' })
            ));
    });
});
