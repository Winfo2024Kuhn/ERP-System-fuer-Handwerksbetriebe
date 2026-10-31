import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Schalter } from './schalter';

describe('Schalter', () => {
    it('ist ein Schalter mit Namen und Zustand, Klick und Leertaste schalten um', async () => {
        const onChange = vi.fn();
        const user = userEvent.setup();
        render(<><p id="erklaerung">Erklärung</p><Schalter an={false} onChange={onChange} beschriftung="Ersatz erlauben" beschreibungId="erklaerung" /></>);

        const schalter = screen.getByRole('switch', { name: 'Ersatz erlauben' });
        expect(schalter).toHaveAttribute('aria-checked', 'false');
        expect(schalter).toHaveAccessibleDescription('Erklärung');
        await user.click(schalter);
        expect(onChange).toHaveBeenLastCalledWith(true);
        await user.keyboard(' ');
        expect(onChange).toHaveBeenCalledTimes(2);
    });

    it('meldet „an“ und nimmt gesperrt keinen Klick an', async () => {
        const onChange = vi.fn();
        const user = userEvent.setup();
        render(<Schalter an onChange={onChange} beschriftung="Ersatz erlauben" disabled title="Wird gespeichert" />);

        const schalter = screen.getByRole('switch', { name: 'Ersatz erlauben' });
        expect(schalter).toHaveAttribute('aria-checked', 'true');
        expect(schalter).toBeDisabled();
        expect(schalter).toHaveAttribute('title', 'Wird gespeichert');
        await user.click(schalter);
        expect(onChange).not.toHaveBeenCalled();
    });
});
