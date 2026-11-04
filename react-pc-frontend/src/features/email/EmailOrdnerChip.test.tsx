import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { EmailOrdnerChip } from './EmailOrdnerChip';

describe('EmailOrdnerChip', () => {
    it.each([
        ['inbox', 'Posteingang'], ['sent', 'Gesendet'], ['trash', 'Papierkorb'], ['spam', 'Spam'],
        ['newsletter', 'Newsletter'], ['projects', 'Projekt'], ['offers', 'Anfrage'], ['suppliers', 'Lieferant'],
        ['tax-advisors', 'Steuerberater'], ['inquiries', 'Mögliche Anfrage'],
    ])('zeigt für „%s“ Icon und Namen „%s“', (folder, label) => {
        const { container } = render(<EmailOrdnerChip folder={folder} />);
        const chip = screen.getByTestId('email-ordner-chip');
        expect(chip).toHaveTextContent(`Liegt in: ${label}`);
        expect(screen.getByText('Liegt in:')).toHaveClass('sr-only');
        expect(chip).toHaveAttribute('title', `Liegt in: ${label}`);
        expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
    });

    it('hebt Papierkorb und Spam dezent warnend hervor, die übrigen bleiben grau', () => {
        const { rerender } = render(<EmailOrdnerChip folder="trash" />);
        expect(screen.getByTestId('email-ordner-chip')).toHaveClass('text-amber-800');
        rerender(<EmailOrdnerChip folder="spam" />);
        expect(screen.getByTestId('email-ordner-chip')).toHaveClass('text-amber-800');
        rerender(<EmailOrdnerChip folder="inbox" />);
        expect(screen.getByTestId('email-ordner-chip')).toHaveClass('text-slate-600');
    });

    it.each([undefined, null, 'drafts', 'unbekannt'])('zeigt für „%s“ nichts', (folder) => {
        const { container } = render(<EmailOrdnerChip folder={folder} />);
        expect(container).toBeEmptyDOMElement();
    });
});
