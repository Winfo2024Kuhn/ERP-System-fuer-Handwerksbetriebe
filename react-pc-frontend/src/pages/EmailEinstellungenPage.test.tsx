import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import EmailEinstellungenPage from './EmailEinstellungenPage';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';

/** Eigene Seite für Signaturen und Abwesenheitsnotizen. Nur Dummy-Daten (DSGVO). */
describe('EmailEinstellungenPage', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('zeigt Kopf mit Kategorie und beide Reiter der E-Mail-Einstellungen', async () => {
        vi.stubGlobal('fetch', vi.fn(async (eingabe: RequestInfo | URL) => {
            const pfad = new URL(String(eingabe), 'http://localhost').pathname;
            const body = pfad === '/api/email/outofoffice/postfaecher'
                ? [{ id: 7, emailAdresse: 'max.mustermann@example.com', anzeigename: 'Max Mustermann', eigenes: true }]
                : [];
            return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
        }));
        const user = userEvent.setup();
        render(<ToastProvider><ConfirmProvider><EmailEinstellungenPage /></ConfirmProvider></ToastProvider>);

        expect(screen.getByRole('heading', { level: 1, name: 'E-Mail-Einstellungen' })).toBeInTheDocument();
        expect(screen.getByText('Kommunikation')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Signaturen/ })).toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: /Abwesenheitsnotizen/ }));
        expect(await screen.findByText('Noch keine Abwesenheit geplant')).toBeInTheDocument();
        await waitFor(() => expect(screen.getByRole('button', { name: /Erste Abwesenheit planen/ })).toBeEnabled());
    });
});
