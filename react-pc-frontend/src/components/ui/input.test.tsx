import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { Input } from './input';

describe('Input', () => {
    it('rendert ein Input-Element', () => {
        render(<Input placeholder="Name eingeben" />);
        expect(screen.getByPlaceholderText('Name eingeben')).toBeInTheDocument();
    });

    it('hat korrekte Standard-CSS-Klassen', () => {
        render(<Input data-testid="input" />);
        const input = screen.getByTestId('input');
        expect(input.className).toContain('border-slate-200');
        expect(input.className).toContain('focus:ring-rose-500');
    });

    it('akzeptiert zusätzliche CSS-Klassen', () => {
        render(<Input className="mt-4" data-testid="input" />);
        const input = screen.getByTestId('input');
        expect(input.className).toContain('mt-4');
    });

    it('leitet HTML-Attribute weiter', () => {
        render(<Input type="email" required data-testid="input" />);
        const input = screen.getByTestId('input');
        expect(input).toHaveAttribute('type', 'email');
        expect(input).toBeRequired();
    });

    it('akzeptiert einen Value', () => {
        const handleChange = vi.fn();
        render(<Input value="Test" onChange={handleChange} data-testid="input" />);
        expect(screen.getByTestId('input')).toHaveValue('Test');
    });

    it('unterstützt Ref-Forwarding', () => {
        const ref = vi.fn();
        render(<Input ref={ref} />);
        expect(ref).toHaveBeenCalled();
    });

    it('kann deaktiviert werden', () => {
        render(<Input disabled data-testid="input" />);
        expect(screen.getByTestId('input')).toBeDisabled();
    });

    it('rendert als readonly', () => {
        render(<Input readOnly data-testid="input" />);
        expect(screen.getByTestId('input')).toHaveAttribute('readOnly');
    });
});
