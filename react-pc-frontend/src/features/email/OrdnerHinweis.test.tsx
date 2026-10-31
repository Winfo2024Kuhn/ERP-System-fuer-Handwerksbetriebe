import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { OrdnerHinweis } from './OrdnerHinweis';

const HINWEIS = 'E-Mails in diesem Ordner werden nach 30 Tagen automatisch endgültig gelöscht. '
    + 'Mails, die einem Projekt, Lieferanten oder einer Anfrage zugeordnet sind, bleiben erhalten.';

describe('OrdnerHinweis', () => {
    it.each(['trash', 'newsletter'] as const)('nennt im Ordner %s die 30 Tage und den Schutz zugeordneter Mails, ohne Info-Knopf', (ordner) => {
        render(<OrdnerHinweis ordner={ordner} />);
        expect(screen.getByText(HINWEIS)).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Mehr zu: Spam' })).toBeNull();
    });

    it('erklärt im Spam-Ordner per Info-Knopf das Verschieben auf dem Mail-Server', async () => {
        const user = userEvent.setup();
        render(<OrdnerHinweis ordner="spam" />);
        expect(screen.getByText(HINWEIS)).toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: 'Mehr zu: Spam' }));
        const popover = await screen.findByRole('dialog', { name: 'Spam' });
        expect(popover).toHaveTextContent('Mails in diesem Ordner verschiebt das ERP auch auf dem Mail-Server in den Spam-Ordner, '
            + 'sofern ein Admin das beim Postfach nicht ausgeschaltet hat (Einstellungen → E-Mail → Postfächer). '
            + 'Mails mit Projekt, Lieferant oder Anfrage bleiben im Posteingang.');
        expect(popover).toHaveTextContent('Mit „Kein Spam“ kommt die Mail im ERP und auf dem Server zurück in den Posteingang.');
    });

    it('zeigt im Posteingang keinen Hinweis', () => {
        const { container } = render(<OrdnerHinweis ordner="inbox" />);
        expect(container).toBeEmptyDOMElement();
    });
});
