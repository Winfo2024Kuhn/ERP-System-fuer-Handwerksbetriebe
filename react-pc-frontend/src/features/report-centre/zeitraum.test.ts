import { describe, expect, it } from 'vitest';
import {
    jahresZeitraum, MAX_TAGE_ZEITRAUM, monatsname, monatsZeitraum, pruefeZeitraum,
    tageImZeitraum, vormonat, waehlbareJahre,
} from './zeitraum';

describe('monatsname', () => {
    it('nennt den deutschen Monat', () => {
        expect(monatsname(1)).toBe('Januar');
        expect(monatsname(3)).toBe('März');
        expect(monatsname(12)).toBe('Dezember');
    });
    it('ist leer bei ungültigem Monat', () => {
        expect(monatsname(0)).toBe('');
        expect(monatsname(13)).toBe('');
    });
});

describe('vormonat', () => {
    it('nimmt den Monat davor im selben Jahr', () => {
        expect(vormonat(new Date(2026, 10, 5))).toEqual({ jahr: 2026, monat: 10 });
    });
    it('springt im Januar auf Dezember des Vorjahres', () => {
        expect(vormonat(new Date(2026, 0, 15))).toEqual({ jahr: 2025, monat: 12 });
    });
});

describe('waehlbareJahre', () => {
    it('reicht vier Jahre zurück und eins voraus', () => {
        expect(waehlbareJahre(2026)).toEqual([2022, 2023, 2024, 2025, 2026, 2027]);
    });
});

describe('monatsZeitraum', () => {
    it('liefert ersten und letzten Tag', () => {
        expect(monatsZeitraum(2026, 10)).toEqual({ von: '2026-10-01', bis: '2026-10-31' });
        expect(monatsZeitraum(2026, 4)).toEqual({ von: '2026-04-01', bis: '2026-04-30' });
    });
    it('kennt Schaltjahre', () => {
        expect(monatsZeitraum(2028, 2).bis).toBe('2028-02-29');
        expect(monatsZeitraum(2026, 2).bis).toBe('2026-02-28');
    });
});

describe('jahresZeitraum', () => {
    it('geht vom 1. Januar bis 31. Dezember', () => {
        expect(jahresZeitraum(2026)).toEqual({ von: '2026-01-01', bis: '2026-12-31' });
    });
});

describe('tageImZeitraum', () => {
    it('zählt beide Grenztage mit', () => {
        expect(tageImZeitraum('2026-10-01', '2026-10-31')).toBe(31);
        expect(tageImZeitraum('2026-10-01', '2026-10-01')).toBe(1);
    });
    it('bleibt über die Zeitumstellung genau', () => {
        expect(tageImZeitraum('2026-03-28', '2026-03-30')).toBe(3);
    });
    it('ist negativ bzw. 0, wenn das Ende vor dem Anfang liegt', () => {
        expect(tageImZeitraum('2026-10-02', '2026-10-01')).toBe(0);
    });
    it('lehnt ungültige Daten ab', () => {
        expect(tageImZeitraum('2026-02-30', '2026-03-01')).toBeNull();
        expect(tageImZeitraum('2026-01-01', 'kein Datum')).toBeNull();
        expect(tageImZeitraum('01.10.2026', '2026-10-31')).toBeNull();
        expect(tageImZeitraum('2026-13-01', '2026-12-31')).toBeNull();
    });
});

describe('pruefeZeitraum', () => {
    it('akzeptiert einen Monat und ein Schaltjahr', () => {
        expect(pruefeZeitraum('2026-10-01', '2026-10-31')).toBeNull();
        expect(pruefeZeitraum('2028-01-01', '2028-12-31')).toBeNull();
    });
    it('verlangt beide Daten', () => {
        expect(pruefeZeitraum('', '2026-10-31')).toBe('Bitte Anfang und Ende des Zeitraums wählen.');
        expect(pruefeZeitraum('2026-10-01', '')).toBe('Bitte Anfang und Ende des Zeitraums wählen.');
    });
    it('meldet ungültige Daten', () => {
        expect(pruefeZeitraum('2026-02-31', '2026-03-01')).toBe('Bitte ein gültiges Datum wählen.');
    });
    it('meldet vertauschte Grenzen', () => {
        expect(pruefeZeitraum('2026-10-31', '2026-10-01')).toBe('Das Ende darf nicht vor dem Anfang liegen.');
    });
    it('begrenzt auf 366 Tage', () => {
        expect(MAX_TAGE_ZEITRAUM).toBe(366);
        expect(pruefeZeitraum('2026-01-01', '2027-01-01')).toBeNull();
        expect(pruefeZeitraum('2026-01-01', '2027-01-02')).toBe('Der Zeitraum darf höchstens 366 Tage lang sein.');
    });
});
