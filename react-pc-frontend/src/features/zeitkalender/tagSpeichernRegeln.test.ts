import { describe, expect, it } from 'vitest';
import { ENDE_VOR_BEGINN_MELDUNG, type Buchung } from './kalenderTypen';
import {
    hatUngespeicherteAenderungen, mussGespeichertWerden, PROJEKT_UND_TAETIGKEIT_MELDUNG, pruefeVorDemSpeichern, speicherInhalt,
} from './tagSpeichernRegeln';

const b = (id: number, patch: Partial<Buchung> = {}): Buchung => ({
    id, projektId: 1, arbeitsgangId: 1, projektName: '', arbeitsgangName: '', startZeit: '08:00:00', endeZeit: '12:00:00',
    dauerMinuten: null, dauerFormatiert: null, notiz: null, typ: 'ARBEIT', ...patch,
});

describe('pruefeVorDemSpeichern', () => {
    it('lässt unveränderte Buchungen samt Sekunden unberührt', () => {
        const ergebnis = pruefeVorDemSpeichern([b(1)], new Set(), {});
        expect(ergebnis).toEqual({ meldungen: [], buchungen: [b(1)] });
    });

    it('übernimmt geänderte Uhrzeiten normalisiert', () => {
        const ergebnis = pruefeVorDemSpeichern([b(1)], new Set([1]), { '1:startZeit': '9' });
        expect(ergebnis.meldungen).toEqual([]);
        expect(ergebnis.buchungen[0].startZeit).toBe('09:00');
        expect(ergebnis.buchungen[0].endeZeit).toBe('12:00:00');
    });

    it('meldet fehlendes Projekt oder Tätigkeit nur einmal', () => {
        const ergebnis = pruefeVorDemSpeichern([b(-1, { arbeitsgangId: 0 }), b(-2, { projektId: 0 })], new Set(), {});
        expect(ergebnis.meldungen).toEqual([PROJEKT_UND_TAETIGKEIT_MELDUNG]);
    });

    it('lehnt Ende vor Beginn ab, auch bei gespeicherter Arbeitszeit vom Typ ARBEIT', () => {
        const ergebnis = pruefeVorDemSpeichern([b(1)], new Set([1]), { '1:endeZeit': '07:00' });
        expect(ergebnis.meldungen).toEqual([ENDE_VOR_BEGINN_MELDUNG]);
    });

    it('prüft Abwesenheiten nicht, Pausen ohne Projekt schon', () => {
        expect(pruefeVorDemSpeichern([b(-5, { typ: 'URLAUB', projektId: 0 })], new Set(), {}).meldungen).toEqual([]);
        expect(pruefeVorDemSpeichern([b(-6, { typ: 'PAUSE', projektId: -1, arbeitsgangId: undefined })], new Set(), {}).meldungen).toEqual([]);
    });
});

describe('mussGespeichertWerden', () => {
    it('schickt nur neue oder geänderte Arbeitszeiten und Pausen', () => {
        expect(mussGespeichertWerden(b(1), new Set())).toBe(false);
        expect(mussGespeichertWerden(b(1), new Set([1]))).toBe(true);
        expect(mussGespeichertWerden(b(-1), new Set())).toBe(true);
        expect(mussGespeichertWerden(b(-1, { typ: 'PAUSE', projektId: -1 }), new Set())).toBe(true);
        expect(mussGespeichertWerden(b(-1, { typ: 'URLAUB', projektId: 0 }), new Set())).toBe(false);
    });
});

describe('speicherInhalt', () => {
    it('setzt Datum und Sekunden, bei Änderungen den Änderungsgrund', () => {
        expect(speicherInhalt(b(1, { startZeit: '09:00' }), 5, '2026-11-18')).toMatchObject({
            mitarbeiterId: 5, startZeit: '2026-11-18T09:00:00', endeZeit: '2026-11-18T12:00:00', typ: 'ARBEIT',
            aenderungsgrund: 'Korrektur im Zeiterfassungskalender',
        });
        const neu = speicherInhalt(b(-1, { endeZeit: null, typ: null }), 5, '2026-11-18');
        expect(neu).toMatchObject({ endeZeit: null, typ: 'ARBEIT' });
        expect(neu).not.toHaveProperty('aenderungsgrund');
    });
});

describe('hatUngespeicherteAenderungen', () => {
    it('erkennt geänderte, neue und halb getippte Buchungen, nicht aber gebuchte Abwesenheiten', () => {
        expect(hatUngespeicherteAenderungen([b(1)], new Set(), {})).toBe(false);
        expect(hatUngespeicherteAenderungen([b(1)], new Set([1]), {})).toBe(true);
        expect(hatUngespeicherteAenderungen([b(-1)], new Set(), {})).toBe(true);
        expect(hatUngespeicherteAenderungen([b(-1, { typ: 'URLAUB' })], new Set(), {})).toBe(false);
        expect(hatUngespeicherteAenderungen([b(1)], new Set(), { '1:startZeit': '07:30' })).toBe(true);
        expect(hatUngespeicherteAenderungen([b(1)], new Set(), { '1:startZeit': '08:00' })).toBe(false);
    });
});
