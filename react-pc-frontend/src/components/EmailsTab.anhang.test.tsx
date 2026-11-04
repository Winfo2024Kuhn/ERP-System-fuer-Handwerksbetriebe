import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import EmailsTab from './EmailsTab';
import { jsonAntwort, mitReiter, reiterEintrag } from '../features/email/emailsTab/emailsTabTestApi';
import type { EmailThread } from './EmailThreadView';
import { ToastProvider } from './ui/toast';

/**
 * Anhang-Vorschau aus dem geöffneten Verlauf: Bilder im gemeinsamen ImageViewer,
 * PDFs in DocumentPreviewModal. Beide sind eigene modale Ebenen über dem Verlauf:
 * Escape schließt nur die Vorschau, der Fokus geht zurück an den Anhang-Knopf.
 * Dummy-Daten (DSGVO): Max Mustermann, max@example.com.
 */
vi.mock('./ui/PdfCanvasViewer', () => ({
    PdfCanvasViewer: ({ url }: { url: string }) => <div data-testid="pdf-vorschau" data-url={url} />,
}));

const mail = reiterEintrag(7, {
    subject: 'Anfrage Geländer', fromAddress: 'Max Mustermann <max@example.com>', sentAt: '2026-10-09T10:05:00', bodyHtml: '<p>Bitte um Angebot</p>',
});

const verlauf: EmailThread = {
    rootEmailId: 7, focusedEmailId: 7, emails: [{
        id: 7, subject: 'Anfrage Geländer', fromAddress: 'Max Mustermann <max@example.com>',
        direction: 'IN', sentAt: '2026-10-09T10:05:00', htmlBody: '<p>Bitte um Angebot</p>',
        attachments: [{ id: 3, originalFilename: 'Max-Mustermann-Baustelle.jpg' }, { id: 4, originalFilename: 'zeichnung.pdf' }],
    }],
};

afterEach(() => vi.unstubAllGlobals());

async function oeffne() {
    vi.stubGlobal('fetch', vi.fn(mitReiter([mail], () => jsonAntwort(verlauf))));
    render(<MemoryRouter><ToastProvider><EmailsTab bezug="projekt" bezugId={3} projektId={3} /></ToastProvider></MemoryRouter>);
    fireEvent.click(await screen.findByRole('button', { name: 'Anfrage Geländer' }));
    await screen.findByText('1 Nachricht im Verlauf');
}

describe('EmailsTab – Anhang-Vorschau', () => {
    it('zeigt ein Bild als eigenes Fenster; Escape schließt nur die Vorschau, Fokus zurück an den Anhang', async () => {
        const user = userEvent.setup();
        await oeffne();
        const anhang = screen.getByRole('button', { name: /Max-Mustermann-Baustelle\.jpg/ });
        await user.click(anhang);
        const vorschau = screen.getByRole('dialog', { name: 'Max-Mustermann-Baustelle.jpg' });
        expect(screen.getByAltText('Max-Mustermann-Baustelle.jpg')).toHaveAttribute('src', '/api/emails/7/attachments/3');
        expect(screen.getByRole('dialog', { name: 'Anfrage Geländer' })).not.toContainElement(vorschau);
        expect(screen.getByRole('button', { name: 'Vorschau schließen' })).toHaveFocus();

        await user.tab({ shift: true });
        await user.tab({ shift: true });
        expect(vorschau).toContainElement(document.activeElement as HTMLElement);

        await user.keyboard('{Escape}');
        expect(screen.queryByRole('dialog', { name: 'Max-Mustermann-Baustelle.jpg' })).not.toBeInTheDocument();
        expect(screen.getByRole('dialog', { name: 'Anfrage Geländer' })).toBeInTheDocument();
        expect(anhang).toHaveFocus();
    });

    it('zeigt ein PDF in der gemeinsamen Dokumentvorschau; Escape schließt nur die Vorschau', async () => {
        const user = userEvent.setup();
        await oeffne();
        const anhang = screen.getByRole('button', { name: /zeichnung\.pdf/ });
        await user.click(anhang);
        expect(screen.getByRole('dialog', { name: 'zeichnung.pdf' })).toBeInTheDocument();
        expect(screen.getByTestId('pdf-vorschau')).toHaveAttribute('data-url', '/api/emails/7/attachments/4');

        await user.keyboard('{Escape}');
        expect(screen.queryByRole('dialog', { name: 'zeichnung.pdf' })).not.toBeInTheDocument();
        expect(screen.getByRole('dialog', { name: 'Anfrage Geländer' })).toBeInTheDocument();
        expect(anhang).toHaveFocus();

        await user.click(anhang);
        await user.click(screen.getByRole('button', { name: 'Vorschau schließen' }));
        expect(screen.queryByRole('dialog', { name: 'zeichnung.pdf' })).not.toBeInTheDocument();
        expect(screen.getByRole('dialog', { name: 'Anfrage Geländer' })).toBeInTheDocument();
    });
});
