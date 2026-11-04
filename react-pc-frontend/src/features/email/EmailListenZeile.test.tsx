import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import type { EmailItem } from './emailCenterModel';
import { EmailListenZeile, type EmailListenZeileProps } from './EmailListenZeile';

const email: EmailItem = {
    id: 12, type: 'EMAIL', direction: 'IN', subject: 'Angebot Geländer', fromAddress: 'Max Mustermann <max@example.com>',
    body: 'Hallo, anbei die Maße.', sentAt: '2026-09-14T12:30:00', isRead: true, zuordnungTyp: 'KEINE', attachments: [],
    folder: 'trash',
};

function zeige(props: Partial<EmailListenZeileProps> = {}) {
    const rueckrufe = { onOeffnen: vi.fn(), onStern: vi.fn(), onLoeschen: vi.fn(), onEntwurf: vi.fn() };
    render(<MemoryRouter>
        <EmailListenZeile email={email} name="Max Mustermann" ausgewaehlt={false} ungelesen={false}
            zeigeOrdner={false} imPapierkorb={false} ziehbar {...rueckrufe} {...props} />
    </MemoryRouter>);
    return rueckrufe;
}

describe('EmailListenZeile', () => {
    it('zeigt Name, Betreff, Vorschau und Datum', () => {
        zeige();
        expect(screen.getByText('Max Mustermann')).toBeInTheDocument();
        expect(screen.getByText('Angebot Geländer')).toBeInTheDocument();
        expect(screen.getByText('Hallo, anbei die Maße.')).toBeInTheDocument();
        expect(screen.getByText('14.09.')).toBeInTheDocument();
    });

    it('legt mit dem Papierkorb-Knopf in den Papierkorb, ohne die Mail zu öffnen', async () => {
        const user = userEvent.setup();
        const { onLoeschen, onOeffnen } = zeige();
        await user.click(screen.getByRole('button', { name: 'In den Papierkorb (Entf)' }));
        expect(onLoeschen).toHaveBeenCalledWith(email);
        expect(onOeffnen).not.toHaveBeenCalled();
    });

    it('erreicht den Papierkorb-Knopf per Tab direkt nach dem Stern und zeigt ihn bei Fokus', async () => {
        const user = userEvent.setup();
        zeige();
        const stern = screen.getByRole('button', { name: 'Markieren' });
        stern.focus();
        await user.tab();
        const knopf = screen.getByRole('button', { name: 'In den Papierkorb (Entf)' });
        expect(knopf).toHaveFocus();
        expect(knopf).toHaveAttribute('title', 'In den Papierkorb (Entf)');
        expect(knopf).toHaveClass('opacity-0', 'group-hover:opacity-100', 'group-focus-within:opacity-100');
    });

    it('zeigt den Knopf an der gewählten Zeile immer', () => {
        zeige({ ausgewaehlt: true });
        expect(screen.getByRole('button', { name: 'In den Papierkorb (Entf)' })).toHaveClass('opacity-100');
    });

    it('heißt im Papierkorb „Endgültig löschen“', () => {
        zeige({ imPapierkorb: true });
        expect(screen.getByRole('button', { name: 'Endgültig löschen (Entf)' })).toBeInTheDocument();
    });

    it('setzt den Stern, ohne die Mail zu öffnen', async () => {
        const user = userEvent.setup();
        const { onStern, onOeffnen } = zeige();
        await user.click(screen.getByRole('button', { name: 'Markieren' }));
        expect(onStern).toHaveBeenCalledWith(email);
        expect(onOeffnen).not.toHaveBeenCalled();
    });

    it('öffnet die Mail per Klick auf die Zeile', async () => {
        const user = userEvent.setup();
        const { onOeffnen } = zeige();
        await user.click(screen.getByText('Angebot Geländer'));
        expect(onOeffnen).toHaveBeenCalledWith(expect.anything(), email);
    });

    it('zeigt das Ordner-Schild nur auf Wunsch (Suche in allen Ordnern)', () => {
        const { unmount } = render(<MemoryRouter><EmailListenZeile email={email} name="Max" ausgewaehlt={false} ungelesen={false}
            zeigeOrdner={false} imPapierkorb={false} ziehbar onOeffnen={vi.fn()} onStern={vi.fn()} onLoeschen={vi.fn()} onEntwurf={vi.fn()} /></MemoryRouter>);
        expect(screen.queryByTestId('email-ordner-chip')).not.toBeInTheDocument();
        unmount();
        zeige({ zeigeOrdner: true });
        expect(screen.getByTestId('email-ordner-chip')).toHaveTextContent('Papierkorb');
    });

    it('öffnet einen zugehörigen Entwurf über sein Schild', async () => {
        const user = userEvent.setup();
        const entwurf = { id: 71, subject: 'Re: Angebot Geländer' };
        const { onEntwurf, onOeffnen } = zeige({ entwurf });
        await user.click(screen.getByRole('button', { name: 'Entwurf öffnen' }));
        expect(onEntwurf).toHaveBeenCalledWith(entwurf);
        expect(onOeffnen).not.toHaveBeenCalled();
    });
});
