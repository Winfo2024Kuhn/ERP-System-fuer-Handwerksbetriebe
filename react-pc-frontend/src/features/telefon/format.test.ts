import { describe, expect, it } from 'vitest';
import {
    anrufbeantworterName,
    tagAnzeige,
    tagAusAdresse,
    anzeigeNummer,
    formatDauerMinuten,
    formatLaufzeit,
    formatSekunden,
    formatVorZeit,
    formatWann,
    formatWannImSatz,
    leseZeitpunkt,
} from './format';

const JETZT = new Date(2026, 8, 29, 12, 5, 0); // 29.09.2026 12:05 Ortszeit

describe('Telefon-Formatierung', () => {
    it('liest lokale Zeitpunkte ohne Zeitzonen-Verschiebung', () => {
        const d = leseZeitpunkt('2026-01-01T00:30:00');
        expect(d?.getFullYear()).toBe(2026);
        expect(d?.getDate()).toBe(1);
        expect(d?.getHours()).toBe(0);
        expect(leseZeitpunkt('quatsch')).toBeNull();
        expect(leseZeitpunkt(null)).toBeNull();
    });

    it('zeigt heute, gestern und sonst das Datum', () => {
        expect(formatWann('2026-09-29T11:55:00', JETZT)).toBe('Heute 11:55');
        expect(formatWann('2026-09-28T09:52:00', JETZT)).toBe('Gestern 09:52');
        expect(formatWann('2026-09-20T14:10:00', JETZT)).toBe('20.09.2026 14:10');
        expect(formatWann(null, JETZT)).toBe('–');
    });

    it('schreibt im Satz klein bzw. mit "am"', () => {
        expect(formatWannImSatz('2026-09-29T12:04:00', JETZT)).toBe('heute 12:04');
        expect(formatWannImSatz('2026-09-20T08:00:00', JETZT)).toBe('am 20.09.2026 08:00');
    });

    it('beschreibt die letzte Abholung relativ', () => {
        expect(formatVorZeit('2026-09-29T12:04:50', JETZT)).toBe('gerade eben');
        expect(formatVorZeit('2026-09-29T12:04:00', JETZT)).toBe('vor 1 Minute');
        expect(formatVorZeit('2026-09-29T11:55:00', JETZT)).toBe('vor 10 Minuten');
        expect(formatVorZeit('2026-09-29T10:05:00', JETZT)).toBe('vor 2 Stunden');
        expect(formatVorZeit('2026-09-27T10:05:00', JETZT)).toBe('27.09.2026 10:05');
        expect(formatVorZeit(null, JETZT)).toBe('noch nie');
    });

    it('formatiert Dauern', () => {
        expect(formatDauerMinuten(0)).toBe('–');
        expect(formatDauerMinuten(4)).toBe('4 Min.');
        expect(formatDauerMinuten(75)).toBe('1 Std. 15 Min.');
        expect(formatSekunden(42)).toBe('0:42');
        expect(formatSekunden(125.7)).toBe('2:05');
        expect(formatSekunden(Number.NaN)).toBe('0:00');
        expect(formatLaufzeit(83)).toBe('01:23');
        expect(formatLaufzeit(3723)).toBe('1:02:03');
    });

    it('nennt Anrufbeantworter beim Namen', () => {
        const liste = [{ index: 0, name: 'AB Tag' }, { index: 1, name: 'AB Nacht' }];
        expect(anrufbeantworterName(liste, 1)).toBe('AB Nacht');
        expect(anrufbeantworterName(liste, 4)).toBe('Anrufbeantworter 5');
        expect(anrufbeantworterName(liste, null)).toBe('Anrufbeantworter');
    });

    it('zeigt unterdrückte Nummern verständlich', () => {
        expect(anzeigeNummer('')).toBe('Nummer unterdrückt');
        expect(anzeigeNummer('0931 1234567')).toBe('0931 1234567');
    });

    it('liest den Tag aus der Adresse nur, wenn er echt ist', () => {
        expect(tagAusAdresse(new URLSearchParams('tag=2026-09-29'))).toBe('2026-09-29');
        expect(tagAusAdresse(new URLSearchParams(''))).toBe('');
        expect(tagAusAdresse(new URLSearchParams('tag=2026-02-30'))).toBe('');
        expect(tagAusAdresse(new URLSearchParams('tag=29.09.2026'))).toBe('');
        expect(tagAusAdresse(new URLSearchParams('tag=2026-09-29x'))).toBe('');
        expect(tagAusAdresse(new URLSearchParams("tag='; DROP TABLE x; --"))).toBe('');
    });

    it('zeigt den Tag deutsch an', () => {
        expect(tagAnzeige('2026-09-29')).toBe('29.09.2026');
        expect(tagAnzeige('kaputt')).toBe('kaputt');
    });
});
