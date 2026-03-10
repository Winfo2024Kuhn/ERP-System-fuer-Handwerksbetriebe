import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { Label } from './label';

describe('Label', () => {
    it('rendert den Labeltext', () => {
        render(<Label>Vorname</Label>);
        expect(screen.getByText('Vorname')).toBeInTheDocument();
    });

    it('hat korrekte Standard-CSS-Klassen', () => {
        render(<Label data-testid="label">Name</Label>);
        const label = screen.getByTestId('label');
        expect(label.className).toContain('text-sm');
        expect(label.className).toContain('text-slate-800');
    });

    it('akzeptiert zusätzliche CSS-Klassen', () => {
        render(<Label className="font-bold" data-testid="label">Bold</Label>);
        expect(screen.getByTestId('label').className).toContain('font-bold');
    });

    it('leitet htmlFor-Attribut weiter', () => {
        render(<Label htmlFor="email">E-Mail</Label>);
        expect(screen.getByText('E-Mail')).toHaveAttribute('for', 'email');
    });
});
