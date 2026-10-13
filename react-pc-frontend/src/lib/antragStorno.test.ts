import { describe, expect, it } from 'vitest';
import { stornoRueckfrage } from './antragStorno';

describe('stornoRueckfrage', () => {
    it('nennt nur beim Urlaub den Resturlaub', () => {
        expect(stornoRueckfrage('URLAUB')).toEqual({
            title: 'Urlaubsantrag stornieren?',
            message: 'Der Antrag wird storniert. Bereits eingetragene Tage werden aus dem Kalender gelöscht, der Resturlaub steigt wieder.',
        });
        for (const typ of ['KRANKHEIT', 'FORTBILDUNG', 'ZEITAUSGLEICH']) {
            expect(stornoRueckfrage(typ).message).toContain('Bereits eingetragene Tage werden aus dem Kalender gelöscht.');
            expect(stornoRueckfrage(typ).message).not.toContain('Resturlaub');
        }
    });

    it('passt den Titel an die Art an', () => {
        expect(stornoRueckfrage('KRANKHEIT').title).toBe('Krankmeldung stornieren?');
        expect(stornoRueckfrage('FORTBILDUNG').title).toBe('Fortbildung stornieren?');
        expect(stornoRueckfrage('ZEITAUSGLEICH').title).toBe('Zeitausgleich stornieren?');
    });

    it('fällt bei unbekannter Art auf einen neutralen Text zurück', () => {
        expect(stornoRueckfrage('SONSTIGES')).toEqual({
            title: 'Antrag stornieren?',
            message: 'Der Antrag wird storniert. Bereits eingetragene Tage werden aus dem Kalender gelöscht.',
        });
    });
});
