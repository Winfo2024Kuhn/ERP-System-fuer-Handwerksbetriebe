import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { SummenFooter } from './SummenFooter';

const basis = { nettosumme: 100, blockCount: 1, dokumentTypLabel: 'Gutschrift', datum: '2026-10-20', isLocked: true };

describe('SummenFooter', () => {
    it('weist bei einer Gutschrift darauf hin, dass sie als Minus gebucht wird – die Summe bleibt positiv', () => {
        render(<SummenFooter {...basis} istGutschrift />);
        expect(screen.getByText('Wird als Gutschrift (Minus) gebucht')).toBeInTheDocument();
        expect(screen.getByText('Gutschrift')).toBeInTheDocument();
        expect(screen.getByText(/^100,00 €$/)).toBeInTheDocument();
    });

    it('zeigt den Hinweis bei anderen Dokumenten nicht', () => {
        render(<SummenFooter {...basis} dokumentTypLabel="Rechnung" />);
        expect(screen.queryByText('Wird als Gutschrift (Minus) gebucht')).not.toBeInTheDocument();
    });
});
