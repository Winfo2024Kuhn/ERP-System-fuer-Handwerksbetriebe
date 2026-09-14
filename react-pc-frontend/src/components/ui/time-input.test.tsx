import { fireEvent, render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { TimeInput, validateTimeInput, normalizeForgivingTime } from './time-input';

describe('TimeInput', () => {
    it('hat ausschließlich gestaltete Texteingabe', () => {
        render(<TimeInput label="Frühester Start" value="09:05" onChange={vi.fn()} />);
        expect(screen.getByRole('textbox', { name: 'Frühester Start' })).toHaveAttribute('type', 'text');
    });

    it.each(['25:00', '09:99', '12:00x', 'abc', '24:00', '12:60'])('lehnt %s vollständig ab', value => {
        expect(validateTimeInput(value, { label: 'Beginn' }).valid).toBe(false);
    });

    it('prüft Pflicht und Grenzen', () => {
        expect(validateTimeInput('', { label: 'Beginn' })).toEqual({ valid: true, value: null });
        expect(validateTimeInput('', { label: 'Beginn', required: true }).valid).toBe(false);
        expect(validateTimeInput('00:00', { label: 'Beginn' })).toEqual({ valid: true, value: '00:00' });
        expect(validateTimeInput('23:59', { label: 'Beginn' })).toEqual({ valid: true, value: '23:59' });
    });

    it('parst fehlertolerante Eingaben wie 11, 11., 11:30, 8,5, 11 30 und 1130', () => {
        expect(normalizeForgivingTime('11')).toBe('11:00');
        expect(normalizeForgivingTime('11.')).toBe('11:00');
        expect(normalizeForgivingTime('11,')).toBe('11:00');
        expect(normalizeForgivingTime('11:')).toBe('11:00');
        expect(normalizeForgivingTime('8')).toBe('08:00');
        expect(normalizeForgivingTime(' 11 : 30 ')).toBe('11:30');
        expect(normalizeForgivingTime('11 30')).toBe('11:30');
        expect(normalizeForgivingTime('11.30')).toBe('11:30');
        expect(normalizeForgivingTime('11,30')).toBe('11:30');
        expect(normalizeForgivingTime('8,5')).toBe('08:30');
        expect(normalizeForgivingTime('8.5')).toBe('08:30');
        expect(normalizeForgivingTime('830')).toBe('08:30');
        expect(normalizeForgivingTime('1130')).toBe('11:30');
        expect(normalizeForgivingTime('9:05')).toBe('09:05');

        expect(validateTimeInput('11', { label: 'Von' })).toEqual({ valid: true, value: '11:00' });
        expect(validateTimeInput(' 11.30 ', { label: 'Bis' })).toEqual({ valid: true, value: '11:30' });
    });

    it('normalisiert Wert beim Verlassen des Feldes (onBlur)', () => {
        const handleChange = vi.fn();
        render(<TimeInput label="Beginn" value="11" onChange={handleChange} />);
        const input = screen.getByRole('textbox', { name: 'Beginn' });
        fireEvent.blur(input, { target: { value: '11' } });
        expect(handleChange).toHaveBeenCalledWith('11:00');
    });
});

