import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { Slider } from './slider';

describe('Slider', () => {
    it('ist per Tastatur bedienbar und meldet Ändern und Loslassen', () => {
        const onValueChange = vi.fn();
        const onValueCommit = vi.fn();
        render(<Slider value={[160]} min={96} max={320} step={8} beschriftung="Größe der Vorschaubilder"
            onValueChange={onValueChange} onValueCommit={onValueCommit} />);
        const regler = screen.getByRole('slider', { name: 'Größe der Vorschaubilder' });
        expect(regler).toHaveAttribute('aria-valuenow', '160');
        expect(regler).toHaveAttribute('aria-valuemin', '96');
        expect(regler).toHaveAttribute('aria-valuemax', '320');
        fireEvent.keyDown(regler, { key: 'ArrowRight' });
        expect(onValueChange).toHaveBeenLastCalledWith([168]);
        expect(onValueCommit).toHaveBeenLastCalledWith([168]);
        fireEvent.keyDown(regler, { key: 'End' });
        expect(onValueChange).toHaveBeenLastCalledWith([320]);
    });

    it('nutzt die Firmenfarbe für Spur und Fokus-Ring', () => {
        const { container } = render(<Slider defaultValue={[50]} beschriftung="Lautstärke" />);
        expect(container.querySelector('[data-slot="slider-range"]')?.className).toContain('bg-rose-600');
        expect(screen.getByRole('slider', { name: 'Lautstärke' }).className).toContain('focus-visible:ring-rose-500');
    });
});
