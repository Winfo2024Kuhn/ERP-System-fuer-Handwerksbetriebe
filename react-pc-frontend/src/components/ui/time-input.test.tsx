import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { TimeInput, validateTimeInput } from './time-input';
describe('TimeInput', () => {
    it('hat ausschließlich gestaltete Texteingabe', () => {
        render(<TimeInput label="Frühester Start" value="09:05" onChange={vi.fn()} />);
        expect(screen.getByRole('textbox', { name: 'Frühester Start' })).toHaveAttribute('type', 'text');
    });
    it.each(['25:00', '09:99', '9:05', '09:', '12:00x'])('lehnt %s vollständig ab', value => {
        expect(validateTimeInput(value, { label: 'Beginn' }).valid).toBe(false);
    });
    it('prüft Pflicht und Grenzen', () => {
        expect(validateTimeInput('', { label: 'Beginn' })).toEqual({ valid: true, value: null });
        expect(validateTimeInput('', { label: 'Beginn', required: true }).valid).toBe(false);
        expect(validateTimeInput('00:00', { label: 'Beginn' })).toEqual({ valid: true, value: '00:00' });
        expect(validateTimeInput('23:59', { label: 'Beginn' })).toEqual({ valid: true, value: '23:59' });
    });
});
