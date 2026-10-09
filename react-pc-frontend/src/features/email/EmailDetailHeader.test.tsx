import { render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../../components/ui/toast';
import { EmailDetailHeader } from './EmailDetailHeader';
import type { EmailItem } from './emailCenterModel';

const basis: EmailItem = {
    id: 1, type: 'EMAIL', direction: 'IN',
    subject: 'Re: Garagentor', sender: 'Max Mustermann',
    fromAddress: 'Max Mustermann <max@example.com>', recipient: 'info@musterbetrieb.example',
    sentAt: '2026-10-01T09:30:00', zuordnungTyp: 'KEINE', attachments: [],
};

function renderKopf(email: EmailItem) {
    const leer = vi.fn();
    return render(<MemoryRouter>
        <ToastProvider>
            <EmailDetailHeader email={email} folder="inbox" onReply={leer} onForward={leer} onAssign={leer}
                onStar={leer} onDelete={leer} onMove={leer} onSpam={leer} onNotSpam={leer} onBlock={leer}
                onNotNewsletter={leer} onConfirmNewsletter={leer} />
        </ToastProvider>
    </MemoryRouter>);
}

describe('EmailDetailHeader – Zuordnung', () => {
    it('zeigt „Gehört zu:“ mit Link ins Projekt samt Auftragsnummer', () => {
        renderKopf({
            ...basis, zuordnungTyp: 'PROJEKT', projektId: 41, projektName: 'Garagentor Mustermann',
            projektAuftragsnummer: '2026-041',
        });
        const zeile = screen.getByTestId('email-gehoert-zu');
        expect(zeile).toHaveTextContent('Gehört zu:');
        const link = within(zeile).getByRole('link', { name: 'Projekt 2026-041 · Garagentor Mustermann öffnen' });
        expect(link).toHaveAttribute('href', '/projekte?projektId=41');
    });

    it('verlinkt eine Anfrage', () => {
        renderKopf({ ...basis, zuordnungTyp: 'ANFRAGE', anfrageId: 12, anfrageName: 'Garagentor Mustermann' });
        expect(screen.getByRole('link', { name: 'Anfrage · Garagentor Mustermann öffnen' }))
            .toHaveAttribute('href', '/anfragen?anfrageId=12');
    });

    it('zeigt Steuerberater ohne Link', () => {
        renderKopf({ ...basis, zuordnungTyp: 'STEUERBERATER' });
        const zeile = screen.getByTestId('email-gehoert-zu');
        expect(zeile).toHaveTextContent('Steuerberater');
        expect(within(zeile).queryByRole('link')).not.toBeInTheDocument();
    });

    it('zeigt keine Zeile, wenn die Mail nirgends zugeordnet ist', () => {
        renderKopf(basis);
        expect(screen.queryByTestId('email-gehoert-zu')).not.toBeInTheDocument();
        expect(screen.queryByText('KEINE')).not.toBeInTheDocument();
    });
});
