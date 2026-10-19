import { describe, expect, it } from 'vitest';
import {
    addTage, anzeigeSpanne, endeVorBeginn, formatLocalDate, jahreImZeitraum, montagDerWoche, parseLocalDate,
    sichtbarerZeitraum, sortiereTermine, stundenBereich, tageImZeitraum, verteileUeberlappungen, wechsleMonat,
    zeitInMinuten,
} from './terminKalenderLogik';

const tag = (text: string) => parseLocalDate(text);

describe('sichtbarer Zeitraum', () => {
    it('Monat: ganzes Raster mit angeschnittenen Vor- und Folgetagen', () => {
        // Oktober 2026 beginnt an einem Donnerstag und endet an einem Samstag.
        expect(sichtbarerZeitraum('monat', tag('2026-10-19'))).toEqual({ von: '2026-09-28', bis: '2026-11-01' });
    });

    it('Monat: beginnt der Monat am Montag und endet am Sonntag, bleibt es beim Monat', () => {
        // Juni 2026: 1. ist Montag, 30. ist Dienstag → bis Sonntag 5. Juli.
        expect(sichtbarerZeitraum('monat', tag('2026-06-10'))).toEqual({ von: '2026-06-01', bis: '2026-07-05' });
        // Februar 2027: 1. ist Montag, 28. ist Sonntag.
        expect(sichtbarerZeitraum('monat', tag('2027-02-14'))).toEqual({ von: '2027-02-01', bis: '2027-02-28' });
    });

    it('Monat über den Jahreswechsel (Januar zeigt Ende Dezember)', () => {
        expect(sichtbarerZeitraum('monat', tag('2027-01-05'))).toEqual({ von: '2026-12-28', bis: '2027-01-31' });
    });

    it('Woche: Montag bis Sonntag, auch über Monats- und Jahresgrenze', () => {
        expect(sichtbarerZeitraum('woche', tag('2026-10-01'))).toEqual({ von: '2026-09-28', bis: '2026-10-04' });
        expect(sichtbarerZeitraum('woche', tag('2027-01-03'))).toEqual({ von: '2026-12-28', bis: '2027-01-03' });
    });

    it('Tag: nur dieser Tag', () => {
        expect(sichtbarerZeitraum('tag', tag('2026-12-31'))).toEqual({ von: '2026-12-31', bis: '2026-12-31' });
    });
});

describe('Datumshilfen', () => {
    it('formatiert und liest lokale Daten ohne UTC-Verschiebung', () => {
        expect(formatLocalDate(new Date(2026, 0, 5))).toBe('2026-01-05');
        expect(parseLocalDate('2026-03-29').getDate()).toBe(29);
        expect(formatLocalDate(addTage(tag('2026-12-31'), 1))).toBe('2027-01-01');
    });

    it('findet den Montag – auch von einem Sonntag aus', () => {
        expect(formatLocalDate(montagDerWoche(tag('2026-10-25')))).toBe('2026-10-19');
        expect(formatLocalDate(montagDerWoche(tag('2026-10-19')))).toBe('2026-10-19');
    });

    it('listet alle Tage und berührten Jahre', () => {
        expect(tageImZeitraum({ von: '2026-12-30', bis: '2027-01-02' })).toEqual(['2026-12-30', '2026-12-31', '2027-01-01', '2027-01-02']);
        expect(jahreImZeitraum({ von: '2026-12-28', bis: '2027-01-03' })).toEqual([2026, 2027]);
        expect(jahreImZeitraum({ von: '2026-05-01', bis: '2026-05-31' })).toEqual([2026]);
    });

    it('wechselt Monat und Jahr, ohne über das Monatsende zu laufen', () => {
        expect(formatLocalDate(wechsleMonat(tag('2026-01-31'), 2026, 1))).toBe('2026-02-28');
        expect(formatLocalDate(wechsleMonat(tag('2024-02-29'), 2025, 1))).toBe('2025-02-28');
        expect(formatLocalDate(wechsleMonat(tag('2026-10-19'), 2026, 4))).toBe('2026-05-19');
    });
});

describe('Uhrzeiten', () => {
    it('liest HH:mm und HH:mm:ss, sonst null', () => {
        expect(zeitInMinuten('08:30')).toBe(510);
        expect(zeitInMinuten('17:05:00')).toBe(1025);
        expect(zeitInMinuten(null)).toBeNull();
        expect(zeitInMinuten('8 Uhr')).toBeNull();
    });

    it('erkennt Ende vor Beginn; gleiche Uhrzeit und unlesbare Werte sind kein Fehler', () => {
        expect(endeVorBeginn('10:00', '09:30')).toBe(true);
        expect(endeVorBeginn('10:00', '10:00')).toBe(false);
        expect(endeVorBeginn('10:00', '11:00')).toBe(false);
        expect(endeVorBeginn('', '09:00')).toBe(false);
    });

    it('gibt jedem Termin eine sichtbare Spanne', () => {
        expect(anzeigeSpanne({ id: 1, startZeit: null, endeZeit: null })).toBeNull();
        expect(anzeigeSpanne({ id: 1, startZeit: '09:00', endeZeit: null })).toEqual({ start: 540, ende: 600 });
        expect(anzeigeSpanne({ id: 1, startZeit: '09:00', endeZeit: '09:10' })).toEqual({ start: 540, ende: 570 });
        expect(anzeigeSpanne({ id: 1, startZeit: '23:50', endeZeit: null })).toEqual({ start: 1430, ende: 1440 });
    });
});

describe('Tagesansicht', () => {
    it('zeigt normal 07–19 Uhr und erweitert für frühe und späte Termine', () => {
        expect(stundenBereich([])).toEqual({ von: 7, bis: 19 });
        expect(stundenBereich([
            { id: 1, startZeit: '05:30', endeZeit: '06:00' },
            { id: 2, startZeit: '20:00', endeZeit: '21:15' },
            { id: 3, startZeit: null, endeZeit: null },
        ])).toEqual({ von: 5, bis: 22 });
        expect(stundenBereich([{ id: 1, startZeit: '00:00', endeZeit: '23:59' }])).toEqual({ von: 0, bis: 24 });
    });

    it('stellt überlappende Termine nebeneinander, getrennte bekommen die volle Breite', () => {
        const plaetze = verteileUeberlappungen([
            { id: 1, startZeit: '08:00', endeZeit: '10:00' },
            { id: 2, startZeit: '09:00', endeZeit: '11:00' },
            { id: 3, startZeit: '10:00', endeZeit: '12:00' }, // überlappt nur mit 2 → nimmt die frei gewordene Spalte 0
            { id: 4, startZeit: '13:00', endeZeit: '14:00' },
            { id: 5, startZeit: null, endeZeit: null },
        ]);
        expect(plaetze.get(1)).toEqual({ spalte: 0, spalten: 2 });
        expect(plaetze.get(2)).toEqual({ spalte: 1, spalten: 2 });
        expect(plaetze.get(3)).toEqual({ spalte: 0, spalten: 2 });
        expect(plaetze.get(4)).toEqual({ spalte: 0, spalten: 1 });
        expect(plaetze.has(5)).toBe(false);
    });

    it('drei gleichzeitige Termine teilen sich drei Spalten', () => {
        const plaetze = verteileUeberlappungen([
            { id: 1, startZeit: '09:00', endeZeit: '10:00' },
            { id: 2, startZeit: '09:00', endeZeit: '10:00' },
            { id: 3, startZeit: '09:30', endeZeit: '10:30' },
        ]);
        expect([...plaetze.values()].map(p => p.spalten)).toEqual([3, 3, 3]);
        expect(new Set([...plaetze.values()].map(p => p.spalte))).toEqual(new Set([0, 1, 2]));
    });

    it('setzt bei gleichem Beginn den längeren Termin nach links', () => {
        const plaetze = verteileUeberlappungen([
            { id: 1, startZeit: '09:00', endeZeit: '10:00' },
            { id: 2, startZeit: '09:00', endeZeit: '12:00' },
        ]);
        expect(plaetze.get(2)).toEqual({ spalte: 0, spalten: 2 });
        expect(plaetze.get(1)).toEqual({ spalte: 1, spalten: 2 });
    });

    it('sortiert ganztägige zuerst, dann nach Beginn und Titel', () => {
        const sortiert = sortiereTermine([
            { titel: 'B', ganztaegig: false, startZeit: '10:00' },
            { titel: 'A', ganztaegig: false, startZeit: '10:00' },
            { titel: 'Früh', ganztaegig: false, startZeit: '07:00' },
            { titel: 'Ganztags', ganztaegig: true, startZeit: null },
            { titel: 'Ohne Zeit', ganztaegig: false, startZeit: null },
        ]);
        expect(sortiert.map(t => t.titel)).toEqual(['Ganztags', 'Ohne Zeit', 'Früh', 'A', 'B']);
        // Unabhängig von der Eingabereihenfolge.
        const ganz = { titel: 'G', ganztaegig: true, startZeit: null };
        const mitZeit = { titel: 'Z', ganztaegig: false, startZeit: '08:00' };
        expect(sortiereTermine([mitZeit, ganz]).map(t => t.titel)).toEqual(['G', 'Z']);
        expect(sortiereTermine([ganz, mitZeit]).map(t => t.titel)).toEqual(['G', 'Z']);
    });
});
