import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import EmailsTab, { type GenericEmail } from './EmailsTab';
import type { EmailThread } from './EmailThreadView';
import { ToastProvider } from './ui/toast';

/**
 * Anhang-Vorschau aus dem geöffneten Verlauf: Bilder im gemeinsamen ImageViewer,
 * PDFs in DocumentPreviewModal. Escape schließt nur die Vorschau, nicht den Verlauf.
 * Dummy-Daten (DSGVO): Max Mustermann, max@example.com.
 */
vi.mock('./DocumentPreviewModal', () => ({
    default: ({ doc, onClose }: { doc: { url: string; title: string }; onClose: () => void }) => (
        <div data-testid="pdf-vorschau" data-url={doc.url}>
            {doc.title}
            <button onClick={onClose}>PDF schließen</button>
        </div>
    ),
}));

const mail: GenericEmail = {
    id: 7, subject: 'Anfrage Geländer', fromAddress: 'Max Mustermann <max@example.com>',
    direction: 'IN', sentAt: '2026-10-09T10:05:00', bodyHtml: '<p>Bitte um Angebot</p>',
};

const verlauf: EmailThread = {
    rootEmailId: 7, focusedEmailId: 7, emails: [{
        id: 7, subject: 'Anfrage Geländer', fromAddress: 'Max Mustermann <max@example.com>',
        direction: 'IN', sentAt: '2026-10-09T10:05:00', htmlBody: '<p>Bitte um Angebot</p>',
        attachments: [{ id: 3, originalFilename: 'foto.jpg' }, { id: 4, originalFilename: 'zeichnung.pdf' }],
    }],
};

afterEach(() => vi.unstubAllGlobals());

async function oeffne() {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({ ok: true, json: () => Promise.resolve(verlauf) } as Response)));
    render(<MemoryRouter><ToastProvider><EmailsTab emails={[mail]} projektId={3} /></ToastProvider></MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: 'Anfrage Geländer' }));
    await screen.findByText('1 Nachricht im Verlauf');
}

describe('EmailsTab – Anhang-Vorschau', () => {
    it('zeigt ein Bild im ImageViewer; Escape schließt nur die Vorschau', async () => {
        await oeffne();
        fireEvent.click(screen.getByRole('button', { name: /foto\.jpg/ }));
        const bild = screen.getByAltText('foto.jpg');
        expect(bild).toHaveAttribute('src', '/api/emails/7/attachments/3');
        // Im Dialog eingehängt, damit Fokusfalle und Escape des Dialogs die Vorschau einschließen.
        expect(screen.getByRole('dialog')).toContainElement(bild);

        await userEvent.setup().keyboard('{Escape}');
        expect(screen.queryByAltText('foto.jpg')).not.toBeInTheDocument();
        expect(screen.getByRole('dialog', { name: 'Anfrage Geländer' })).toBeInTheDocument();
    });

    it('zeigt ein PDF in der gemeinsamen Dokumentvorschau; Escape schließt nur die Vorschau', async () => {
        await oeffne();
        fireEvent.click(screen.getByRole('button', { name: /zeichnung\.pdf/ }));
        const vorschau = screen.getByTestId('pdf-vorschau');
        expect(vorschau).toHaveAttribute('data-url', '/api/emails/7/attachments/4');
        expect(vorschau).toHaveTextContent('zeichnung.pdf');

        await userEvent.setup().keyboard('{Escape}');
        expect(screen.queryByTestId('pdf-vorschau')).not.toBeInTheDocument();
        expect(screen.getByRole('dialog')).toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: /zeichnung\.pdf/ }));
        fireEvent.click(screen.getByText('PDF schließen'));
        expect(screen.queryByTestId('pdf-vorschau')).not.toBeInTheDocument();
    });
});
