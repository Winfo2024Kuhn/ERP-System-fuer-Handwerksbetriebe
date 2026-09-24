import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { GesperrtHinweis } from './GesperrtHinweis';

describe('GesperrtHinweis', () => {
    it('zeigt den exakten Wortlaut mit Namen des Bearbeiters', () => {
        const { container } = render(<GesperrtHinweis halterName="Max Mustermann" />);
        expect(container.textContent).toBe(
            'Max Mustermann bearbeitet das gerade — Sie sehen den aktuellen Stand.',
        );
    });

    it('zeigt den exakten Fallback-Wortlaut ohne Namen ("Ein Kollege")', () => {
        const { container } = render(<GesperrtHinweis />);
        expect(container.textContent).toBe(
            'Ein Kollege bearbeitet das gerade — Sie sehen den aktuellen Stand.',
        );
    });

    it('faellt auf "Ein Kollege" zurueck, wenn halterName nur Leerraum ist', () => {
        const { container } = render(<GesperrtHinweis halterName="   " />);
        expect(container.textContent).toBe(
            'Ein Kollege bearbeitet das gerade — Sie sehen den aktuellen Stand.',
        );
    });

    it('haengt den optionalen Minuten-Zusatz exakt an, wenn seit gesetzt ist', () => {
        const { container } = render(<GesperrtHinweis halterName="Erika Mustermann" seit="5" />);
        expect(container.textContent).toBe(
            'Erika Mustermann bearbeitet das gerade — Sie sehen den aktuellen Stand. Seit 5 Min.',
        );
    });

    it('zeigt keinen Minuten-Zusatz, wenn seit fehlt', () => {
        const { container } = render(<GesperrtHinweis halterName="Max Mustermann" />);
        expect(container.textContent).not.toContain('Seit');
        expect(container.textContent).not.toContain('Min.');
    });

    it('setzt role="status" und aria-live="polite" fuer Screenreader ohne Fokusklau', () => {
        render(<GesperrtHinweis halterName="Max Mustermann" />);
        const status = screen.getByRole('status');
        expect(status).toHaveAttribute('aria-live', 'polite');
    });

    it('enthaelt keinen Button -- der Baustein bietet keine eigene Aktion an', () => {
        const { container } = render(<GesperrtHinweis halterName="Max Mustermann" />);
        expect(container.querySelector('button')).toBeNull();
    });

    it('nutzt ausschliesslich rose/slate-Farben, keine amber/yellow/blue/indigo/sky-Klassen', () => {
        const { container } = render(<GesperrtHinweis halterName="Max Mustermann" seit="12" />);
        const fullClassList = Array.from(container.querySelectorAll('*'))
            .map((el) => el.className)
            .join(' ');
        expect(fullClassList).toMatch(/rose-/);
        expect(fullClassList).not.toMatch(/amber-|yellow-|blue-|indigo-|sky-/);
    });

    it('rendert das Lock-Icon in w-4 h-4 text-rose-600, dekorativ (aria-hidden)', () => {
        const { container } = render(<GesperrtHinweis halterName="Max Mustermann" />);
        const icon = container.querySelector('svg');
        expect(icon).not.toBeNull();
        expect(icon).toHaveAttribute('aria-hidden', 'true');
        expect(icon?.getAttribute('class')).toContain('w-4');
        expect(icon?.getAttribute('class')).toContain('h-4');
        expect(icon?.getAttribute('class')).toContain('text-rose-600');
    });

    it('uebernimmt eine zusaetzliche className am Wurzelelement', () => {
        render(<GesperrtHinweis halterName="Max Mustermann" className="mt-4" />);
        expect(screen.getByRole('status').className).toContain('mt-4');
    });
});
