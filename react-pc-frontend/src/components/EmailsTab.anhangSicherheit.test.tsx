import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import EmailsTab from './EmailsTab';
import { jsonAntwort, mitReiter, reiterEintrag } from '../features/email/emailsTab/emailsTabTestApi';
import type { EmailThread } from './EmailThreadView';
import { ToastProvider } from './ui/toast';

/**
 * Sicherheit der Anhang-Vorschau im Verlauf (Regression für die gelöschte
 * EmailsTabAttachmentPreview): gefährliche Adressen landen nie in <img> oder <iframe>,
 * stattdessen kommt eine Meldung. Der Verlauf selbst ist ersetzt, damit die Adresse
 * frei gewählt werden kann. Dummy-Daten (DSGVO): Max Mustermann.
 */
vi.mock('./EmailThreadView', async importOriginal => ({
    ...(await importOriginal<typeof import('./EmailThreadView')>()),
    EmailThreadView: ({ onPreview }: { onPreview?: (url: string, type: 'image' | 'pdf', name: string) => void }) => (
        <div>
            <button onClick={() => onPreview?.('javascript:alert(1)', 'image', 'x.png')}>Bild javascript</button>
            <button onClick={() => onPreview?.('data:text/html,<script>alert(1)</script>', 'pdf', 'x.pdf')}>PDF data</button>
            <button onClick={() => onPreview?.('//fremd.example.com/y.pdf', 'pdf', 'y.pdf')}>PDF fremd</button>
        </div>
    ),
}));
vi.mock('./DocumentPreviewModal', () => ({ default: () => <div data-testid="pdf-vorschau" /> }));

const mail = reiterEintrag(7, {
    subject: 'Anfrage Geländer', fromAddress: 'max@example.com', sentAt: '2026-10-09T10:05:00',
});
const verlauf: EmailThread = { rootEmailId: 7, focusedEmailId: 7, emails: [{ id: 7, subject: 'Anfrage Geländer', attachments: [] }] };

afterEach(() => vi.unstubAllGlobals());

describe('EmailsTab – unsichere Anhang-Adressen', () => {
    it.each(['Bild javascript', 'PDF data', 'PDF fremd'])('„%s“: keine Vorschau, sondern eine Meldung; Escape schließt danach den Verlauf', async (knopf) => {
        vi.stubGlobal('fetch', vi.fn(mitReiter([mail], () => jsonAntwort(verlauf))));
        const { container } = render(<MemoryRouter><ToastProvider><EmailsTab bezug="projekt" bezugId={3} projektId={3} /></ToastProvider></MemoryRouter>);
        fireEvent.click(await screen.findByRole('button', { name: 'Anfrage Geländer' }));
        fireEvent.click(await screen.findByText(knopf));

        expect(screen.getByText('Der Anhang kann nicht angezeigt werden. Bitte herunterladen.')).toBeInTheDocument();
        expect(screen.queryByTestId('pdf-vorschau')).not.toBeInTheDocument();
        expect(document.body.querySelector('img, iframe')).toBeNull();
        expect(container.innerHTML).not.toMatch(/javascript:|data:text|fremd\.example/);

        fireEvent.keyDown(document.activeElement ?? document.body, { key: 'Escape' });
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
});
