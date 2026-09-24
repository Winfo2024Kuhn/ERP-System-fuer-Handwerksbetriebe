import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { ColorInput } from './color-input';
describe('ColorInput', () => {
    it('erhält eigene Hexfarben und übernimmt nur vollständige Werte', async () => {
        const user = userEvent.setup(); const changed = vi.fn();
        const { container } = render(<ColorInput aria-label="Schriftfarbe" value="#123abc" onChange={changed} />);
        expect(container.querySelector('input[type=color]')).toBeNull();
        await user.click(screen.getByRole('button', { name: 'Schriftfarbe' }));
        const input = screen.getByRole('textbox', { name: 'Hex-Farbwert' }); expect(input).toHaveValue('#123abc');
        await user.clear(input); await user.type(input, '#xx'); await user.click(screen.getByText('Übernehmen'));
        expect(changed).not.toHaveBeenCalled(); expect(screen.getByRole('alert')).toBeVisible();
        await user.clear(input); await user.type(input, '#abc123'); await user.click(screen.getByText('Übernehmen'));
        expect(changed).toHaveBeenCalledWith('#abc123');
    });
    it('bietet eigene Palette und Abbruch per Escape', async () => {
        const user = userEvent.setup(); const changed = vi.fn(); render(<ColorInput aria-label="Farbe" value="#123abc" onChange={changed} />);
        await user.click(screen.getByRole('button', {name:'Farbe'})); await user.keyboard('{Escape}');
        expect(changed).not.toHaveBeenCalled(); expect(screen.getByRole('button', {name:'Farbe'})).toHaveFocus();
        await user.click(screen.getByRole('button', {name:'Farbe'})); await user.click(screen.getByRole('button', {name:'Rose (#e11d48)'}));
        expect(changed).toHaveBeenCalledWith('#e11d48');
    });
});
