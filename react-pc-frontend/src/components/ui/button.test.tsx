import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { Button } from './button';

describe('Button', () => {
    it('rendert mit Standardtext', () => {
        render(<Button>Klick mich</Button>);
        expect(screen.getByRole('button', { name: 'Klick mich' })).toBeInTheDocument();
    });

    it('verwendet Standard-Variante (default)', () => {
        render(<Button>Speichern</Button>);
        const btn = screen.getByRole('button');
        expect(btn.className).toContain('bg-rose-600');
        expect(btn.className).toContain('text-white');
    });

    it('verwendet Outline-Variante', () => {
        render(<Button variant="outline">Abbrechen</Button>);
        const btn = screen.getByRole('button');
        expect(btn.className).toContain('text-rose-700');
        expect(btn.className).toContain('border');
    });

    it('verwendet Ghost-Variante', () => {
        render(<Button variant="ghost">Bearbeiten</Button>);
        const btn = screen.getByRole('button');
        expect(btn.className).toContain('bg-transparent');
        expect(btn.className).toContain('text-rose-700');
    });

    it('verwendet Secondary-Variante', () => {
        render(<Button variant="secondary">Zurück</Button>);
        const btn = screen.getByRole('button');
        expect(btn.className).toContain('bg-slate-100');
        expect(btn.className).toContain('text-slate-800');
    });

    it('verwendet kleine Größe', () => {
        render(<Button size="sm">Klein</Button>);
        const btn = screen.getByRole('button');
        expect(btn.className).toContain('px-3');
        expect(btn.className).toContain('py-1.5');
    });

    it('löst onClick-Handler aus', async () => {
        const handleClick = vi.fn();
        const user = userEvent.setup();
        render(<Button onClick={handleClick}>Klick</Button>);
        await user.click(screen.getByRole('button'));
        expect(handleClick).toHaveBeenCalledOnce();
    });

    it('ist deaktivierbar', () => {
        render(<Button disabled>Gesperrt</Button>);
        const btn = screen.getByRole('button');
        expect(btn).toBeDisabled();
        expect(btn.className).toContain('disabled:opacity-50');
    });

    it('akzeptiert zusätzliche CSS-Klassen', () => {
        render(<Button className="w-full">Voll</Button>);
        const btn = screen.getByRole('button');
        expect(btn.className).toContain('w-full');
    });

    it('gibt keine Klicks weiter wenn deaktiviert', async () => {
        const handleClick = vi.fn();
        const user = userEvent.setup();
        render(<Button disabled onClick={handleClick}>Gesperrt</Button>);
        await user.click(screen.getByRole('button'));
        expect(handleClick).not.toHaveBeenCalled();
    });
});
