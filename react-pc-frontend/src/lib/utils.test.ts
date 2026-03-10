import { describe, it, expect } from 'vitest';
import { cn } from './utils';

describe('cn (Utility)', () => {
    it('merged einfache Klassen', () => {
        expect(cn('text-sm', 'font-bold')).toBe('text-sm font-bold');
    });

    it('entfernt Konflikte zugunsten der letzten Klasse', () => {
        const result = cn('text-sm', 'text-lg');
        expect(result).toBe('text-lg');
    });

    it('behandelt bedingte Klassen', () => {
        const isActive = true;
        const result = cn('base', isActive && 'active');
        expect(result).toBe('base active');
    });

    it('ignoriert falsy Werte', () => {
        const result = cn('base', false, null, undefined, '', 'extra');
        expect(result).toBe('base extra');
    });

    it('merged Tailwind-Klassen korrekt (padding)', () => {
        const result = cn('px-4 py-2', 'px-6');
        expect(result).toBe('py-2 px-6');
    });

    it('gibt leeren String bei keinen Argumenten zurück', () => {
        expect(cn()).toBe('');
    });

    it('verarbeitet Array-Eingaben', () => {
        const result = cn(['text-sm', 'font-bold']);
        expect(result).toBe('text-sm font-bold');
    });
});
