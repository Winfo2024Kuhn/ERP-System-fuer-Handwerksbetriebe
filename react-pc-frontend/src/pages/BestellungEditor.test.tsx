/**
 * Vitest-Suite fuer den Mail-Dialog der Preisanfrage (BestellungEmailModal) –
 * Schwerpunkt: Nachrichtentext (XSS).
 *
 * Die Signatur kommt als HTML vom Server und landet im contentEditable-Feld.
 * Angezeigt werden darf sie nur bereinigt; Formatierung und die
 * email-signature-Klasse bleiben erhalten.
 *
 * DSGVO: ausschliesslich Dummy-Daten.
 */
import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BestellungEmailModal } from './BestellungEditor';
import { ToastProvider } from '../components/ui/toast';
import { ConfirmProvider } from '../components/ui/confirm-dialog';

vi.mock('../components/ui/PdfCanvasViewer', () => ({
    PdfCanvasViewer: ({ url }: { url: string }) => <div data-testid="pdf">{url}</div>,
}));

const SIGNATUR_HTML =
    '<p><strong>Max Mustermann</strong></p><img src="x" onerror="alert(1)"><script>alert(2)</script>';

function antwort(daten: unknown) {
    return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(daten) });
}

beforeEach(() => {
    localStorage.setItem('frontendUserSelection', JSON.stringify({ id: 1, displayName: 'Max Mustermann' }));
    vi.stubGlobal('fetch', vi.fn((url: string) => {
        if (url.startsWith('/api/email/signatures/default')) return antwort({ html: SIGNATUR_HTML });
        if (url.startsWith('/api/lieferanten/')) return antwort({ kundenEmails: ['anfrage@example.com'] });
        return antwort([]);
    }));
});

afterEach(() => {
    localStorage.clear();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('BestellungEmailModal Nachrichtentext', () => {
    it('zeigt die Signatur im Editor nur bereinigt an', async () => {
        render(
            <ToastProvider><ConfirmProvider>
                <BestellungEmailModal isOpen onClose={vi.fn()} lieferantId={1}
                    lieferantName="Mustermann Stahlhandel" onSuccess={vi.fn()} />
            </ConfirmProvider></ToastProvider>,
        );

        const name = await screen.findByText('Max Mustermann', { selector: 'strong' });
        const editor = name.closest('[contenteditable]')!;

        expect(editor).toHaveTextContent('bitte erstellen Sie uns ein Angebot');
        expect(editor.querySelector('.email-signature')).not.toBeNull();
        expect(editor.querySelector('script')).toBeNull();
        expect(editor.querySelector('[onerror]')).toBeNull();
    });
});
