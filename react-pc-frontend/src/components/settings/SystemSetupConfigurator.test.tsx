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
