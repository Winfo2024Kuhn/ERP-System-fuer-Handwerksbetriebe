import { describe, expect, it } from 'vitest';
import type { Buchung } from './kalenderTypen';
import {
    dauerAnzeige, endeVorBeginn, hatUeberschneidungen, parseZeit, schneidePauseEin, zeitEntwurfAus, zeitenFuerNeueBuchung,
} from './buchungZeiten';

const b = (id: number, start: string, ende: string | null, typ: Buchung['typ'] = 'ARBEIT'): Buchung => ({
    id, projektId: 1, arbeitsgangId: 1, projektName: '', arbeitsgangName: '', startZeit: start, endeZeit: ende,
    dauerMinuten: null, dauerFormatiert: null, notiz: null, typ,
});
const ohneEntwurf = zeitEntwurfAus({});

describe('parseZeit', () => {
    it('rechnet Uhrzeiten in Minuten um, auch mit Sekunden', () => {
        expect(parseZeit('08:30')).toBe(510);
        expect(parseZeit('08:30:15')).toBe(510);
        expect(parseZeit('')).toBe(-1);
        expect(parseZeit(null)).toBe(-1);
        expect(parseZeit('25:00')).toBe(-1);
    });
});

describe('zeitEntwurfAus', () => {
    it('nimmt den Entwurf, sonst die gespeicherte Zeit ohne Sekunden', () => {
        expect(ohneEntwurf(b(1, '08:00:00', null), 'startZeit')).toBe('08:00');
        expect(ohneEntwurf(b(-1, '08:00:00', null), 'startZeit')).toBe('08:00:00');
        expect(ohneEntwurf(b(1, '08:00', null), 'endeZeit')).toBe('');
        expect(zeitEntwurfAus({ '1:startZeit': '9' })(b(1, '08:00:00', null), 'startZeit')).toBe('9');
    });
});

describe('endeVorBeginn und dauerAnzeige', () => {
    it('erkennt Ende vor oder gleich Beginn und rechnet sonst die Dauer', () => {
        expect(endeVorBeginn(b(1, '12:00', '08:00'), ohneEntwurf)).toBe(true);
        expect(endeVorBeginn(b(1, '08:00', '08:00'), ohneEntwurf)).toBe(true);
        expect(endeVorBeginn(b(1, '12:00', '08:00', 'URLAUB'), ohneEntwurf)).toBe(false);
        expect(dauerAnzeige(b(1, '12:00', '08:00'), ohneEntwurf)).toBe('Ende vor Beginn');
        expect(dauerAnzeige(b(1, '08:00', '12:15'), ohneEntwurf)).toBe('4:15h');
        expect(dauerAnzeige(b(1, '08:00', null), ohneEntwurf)).toBe('–');
    });
});

describe('hatUeberschneidungen', () => {
    it('meldet überlappende Arbeitszeiten, nicht aber Abwesenheiten oder Anschlusszeiten', () => {
        expect(hatUeberschneidungen([b(1, '08:00', '12:00'), b(2, '11:00', '13:00')], ohneEntwurf)).toBe(true);
        expect(hatUeberschneidungen([b(1, '08:00', '12:00'), b(2, '12:00', '13:00')], ohneEntwurf)).toBe(false);
        expect(hatUeberschneidungen([b(1, '08:00', '12:00'), b(2, '00:00', '23:00', 'URLAUB')], ohneEntwurf)).toBe(false);
    });
});

describe('zeitenFuerNeueBuchung', () => {
    it('beginnt am Ende der letzten Buchung und wählt ein passendes Ende', () => {
        expect(zeitenFuerNeueBuchung([], ohneEntwurf)).toEqual({ start: '08:00', ende: '16:00' });
        expect(zeitenFuerNeueBuchung([b(1, '08:00', '12:00')], ohneEntwurf)).toEqual({ start: '12:00', ende: '16:00' });
        expect(zeitenFuerNeueBuchung([b(1, '08:00', '16:30')], ohneEntwurf)).toEqual({ start: '16:30', ende: '17:00' });
        expect(zeitenFuerNeueBuchung([b(1, '08:00', '23:30')], ohneEntwurf)).toEqual({ start: '23:30', ende: '23:59' });
        expect(zeitenFuerNeueBuchung([b(1, '08:00', '18:00'), b(2, '00:00', '23:00', 'URLAUB')], ohneEntwurf)).toEqual({ start: '18:00', ende: '19:00' });
    });
});

describe('schneidePauseEin', () => {
    const pause = b(-100, '12:00', '12:30', 'PAUSE');

    it('teilt eine Buchung, in deren Mitte die Pause liegt', () => {
        const schnitt = schneidePauseEin({ buchungen: [b(1, '08:00', '16:00')], pauseStart: '12:00', pauseEnde: '12:30', pauseId: -100, neuePause: pause, zeitEntwurf: ohneEntwurf });
        expect(schnitt.buchungen.map(x => [x.id > 0 ? x.id : 'neu', x.startZeit, x.endeZeit])).toEqual([
            [1, '08:00', '12:00'], ['neu', '12:00', '12:30'], ['neu', '12:30', '16:00'],
        ]);
        expect(schnitt.geaenderteIds).toEqual([1]);
        expect(schnitt.entwuerfe['1:endeZeit']).toBe('12:00');
    });

    it('kürzt Buchungen, die Anfang oder Ende der Pause überlappen', () => {
        const schnitt = schneidePauseEin({ buchungen: [b(1, '08:00', '12:15'), b(2, '12:15', '15:00')], pauseStart: '12:00', pauseEnde: '12:30', pauseId: -100, neuePause: pause, zeitEntwurf: ohneEntwurf });
        expect(schnitt.buchungen.map(x => [x.startZeit, x.endeZeit])).toEqual([['08:00', '12:00'], ['12:00', '12:30'], ['12:30', '15:00']]);
        expect(schnitt.geaenderteIds).toEqual([1, 2]);
    });

    it('hängt die Pause bei ungültigen Zeiten nur an', () => {
        const schnitt = schneidePauseEin({ buchungen: [b(1, '08:00', '16:00')], pauseStart: '13:00', pauseEnde: '12:00', pauseId: -100, neuePause: pause, zeitEntwurf: ohneEntwurf });
        expect(schnitt.buchungen).toHaveLength(2);
        expect(schnitt.geaenderteIds).toEqual([]);
    });
});
