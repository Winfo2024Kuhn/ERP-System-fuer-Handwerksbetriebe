import { describe, expect, it } from 'vitest';
import { formatIsoDatumDe, formatUrlaubstage, formatUrlaubstageMitEinheit, vorjahresHinweis } from './urlaubstage';

describe('formatUrlaubstage', () => {
    it('zeigt ganze Tage ohne Nachkomma', () => {
        expect(formatUrlaubstage(12)).toBe('12');
        expect(formatUrlaubstage(0)).toBe('0');
    });

    it('zeigt halbe Tage mit Komma', () => {
        expect(formatUrlaubstage(12.5)).toBe('12,5');
        expect(formatUrlaubstage(0.5)).toBe('0,5');
    });

    it('rundet nicht auf ganze Tage, wenn der Server feiner liefert', () => {
        expect(formatUrlaubstage(1.25)).toBe('1,25');
    });

    it('fällt bei fehlenden oder ungültigen Werten auf 0 zurück', () => {
        expect(formatUrlaubstage(null)).toBe('0');
        expect(formatUrlaubstage(undefined)).toBe('0');
        expect(formatUrlaubstage(Number.NaN)).toBe('0');
    });
});

describe('formatUrlaubstageMitEinheit', () => {
    it('hängt die passende Einheit an', () => {
        expect(formatUrlaubstageMitEinheit(1)).toBe('1 Tag');
        expect(formatUrlaubstageMitEinheit(12.5)).toBe('12,5 Tage');
        expect(formatUrlaubstageMitEinheit(0)).toBe('0 Tage');
    });
});

describe('vorjahresHinweis', () => {
    it('nennt offenen Vorjahresrest mit Verfallsdatum', () => {
        expect(vorjahresHinweis({ ausVorjahrOffen: 2.5, verfallsdatum: '2027-03-31' }))
            .toBe('davon 2,5 Tage aus dem Vorjahr, verfallen am 31.03.2027');
        expect(vorjahresHinweis({ ausVorjahrOffen: 1, verfallsdatum: '2027-03-31' }))
            .toBe('davon 1 Tag aus dem Vorjahr, verfallen am 31.03.2027');
    });

    it('sagt bei „verfällt nicht" kein Datum', () => {
        expect(vorjahresHinweis({ ausVorjahrOffen: 3, verfallsdatum: null })).toBe('davon 3 Tage aus dem Vorjahr, verfallen nicht');
    });

    it('meldet bereits verfallene Tage', () => {
        expect(vorjahresHinweis({ ausVorjahrOffen: 0, verfallen: 2, verfallsdatum: '2027-03-31' }))
            .toBe('2 Tage aus dem Vorjahr sind am 31.03.2027 verfallen');
        expect(vorjahresHinweis({ ausVorjahrOffen: 0, verfallen: 1, verfallsdatum: '2027-03-31' }))
            .toBe('1 Tag aus dem Vorjahr ist am 31.03.2027 verfallen');
    });

    it('liefert null ohne Vorjahresrest oder ohne Daten', () => {
        expect(vorjahresHinweis(null)).toBeNull();
        expect(vorjahresHinweis(undefined)).toBeNull();
        expect(vorjahresHinweis({})).toBeNull();
        expect(vorjahresHinweis({ ausVorjahrOffen: 0, verfallen: 0, verfallsdatum: '2027-03-31' })).toBeNull();
        expect(vorjahresHinweis({ ausVorjahrOffen: 0, verfallen: 2, verfallsdatum: null })).toBeNull();
    });

    it('formatiert ISO-Daten deutsch', () => {
        expect(formatIsoDatumDe('2027-03-31')).toBe('31.03.2027');
    });
});
