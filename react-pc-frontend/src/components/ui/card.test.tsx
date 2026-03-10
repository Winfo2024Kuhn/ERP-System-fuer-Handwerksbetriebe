import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { Card } from './card';

describe('Card', () => {
    it('rendert Kinder-Elemente', () => {
        render(<Card><p>Inhalt</p></Card>);
        expect(screen.getByText('Inhalt')).toBeInTheDocument();
    });

    it('hat Standard-CSS-Klassen', () => {
        render(<Card data-testid="card">Test</Card>);
        const card = screen.getByTestId('card');
        expect(card.className).toContain('bg-white');
        expect(card.className).toContain('border');
        expect(card.className).toContain('rounded-lg');
        expect(card.className).toContain('shadow-sm');
    });

    it('akzeptiert zusätzliche CSS-Klassen', () => {
        render(<Card className="p-4" data-testid="card">Test</Card>);
        expect(screen.getByTestId('card').className).toContain('p-4');
    });
});
