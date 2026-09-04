import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { TabSchliessenHinweis } from './TabSchliessenHinweis';

describe('TabSchliessenHinweis', () => {
    it('zeigt den exakten Hinweistext in der Sie-Form (Sperr-Bausteine siezen, siehe Design-Review Abschnitt 6)', () => {
        render(<TabSchliessenHinweis />);
        expect(screen.getByText(
            'Dokument gespeichert und freigegeben — Sie können diesen Tab jetzt schließen.',
        )).toBeInTheDocument();
    });

    it('enthaelt keine Du-Form ("du kannst")', () => {
        render(<TabSchliessenHinweis />);
        const text = screen.getByRole('status').textContent ?? '';
        expect(text).not.toMatch(/\bdu kannst\b/i);
    });

    it('setzt role="status", damit Screenreader es ohne Fokusklau ansagen', () => {
        render(<TabSchliessenHinweis />);
        expect(screen.getByRole('status')).toBeInTheDocument();
    });

    it('ist kein Modal -- kein role="dialog", kein Abbrechen/Schliessen-Knopf', () => {
        const { container } = render(<TabSchliessenHinweis />);
        expect(container.querySelector('[role="dialog"]')).toBeNull();
        expect(container.querySelector('button')).toBeNull();
    });

    it('nutzt ausschliesslich rose/slate-Farben, keine amber/yellow/blue/indigo/sky-Klassen', () => {
        const { container } = render(<TabSchliessenHinweis />);
        const fullClassList = Array.from(container.querySelectorAll('*'))
            .map((el) => el.className)
            .join(' ');
        expect(fullClassList).toMatch(/rose-/);
        expect(fullClassList).not.toMatch(/amber-|yellow-|blue-|indigo-|sky-/);
    });

    it('rendert ein Lucide-Icon dekorativ (aria-hidden), kein Emoji im Text', () => {
        const { container } = render(<TabSchliessenHinweis />);
        const icon = container.querySelector('svg');
        expect(icon).not.toBeNull();
        expect(icon).toHaveAttribute('aria-hidden', 'true');

        const text = container.textContent ?? '';
        // Grober Emoji-Bereichs-Check reicht hier -- der Text ist ohnehin fest verdrahtet.
        expect(/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}]/u.test(text)).toBe(false);
    });

    it('deckt die ganze Flaeche ab (fixed inset-0), damit nichts vom Editor durchscheint', () => {
        const { container } = render(<TabSchliessenHinweis />);
        const wurzel = container.firstElementChild;
        expect(wurzel?.className).toContain('fixed');
        expect(wurzel?.className).toContain('inset-0');
    });

    it('nutzt text-balance am Hinweistext, damit das letzte Wort nicht allein umbricht (Design-Review Abschnitt 7-1)', () => {
        render(<TabSchliessenHinweis />);
        expect(screen.getByRole('status').className).toContain('text-balance');
    });
});
