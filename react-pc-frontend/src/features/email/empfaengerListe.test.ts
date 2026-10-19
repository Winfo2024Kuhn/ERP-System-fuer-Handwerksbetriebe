import { describe, expect, it } from 'vitest';
import {
    enthaeltEmpfaenger, fuegeEmpfaengerHinzu, kaertchenText, teileEingabe, verbindeEmpfaenger, zerlegeEmpfaenger,
} from './empfaengerListe';

describe('empfaengerListe', () => {
    it('zerlegt und verbindet Empfänger, Kommas in Anführungszeichen bleiben im Namen', () => {
        const liste = zerlegeEmpfaenger('a@example.org; "Mustermann, Max" <max@example.org>,  ');
        expect(liste).toEqual(['a@example.org', '"Mustermann, Max" <max@example.org>']);
        expect(verbindeEmpfaenger(liste)).toBe('a@example.org, "Mustermann, Max" <max@example.org>');
        expect(zerlegeEmpfaenger('')).toEqual([]);
    });

    it('teilt die laufende Eingabe in fertige Empfänger und Rest', () => {
        expect(teileEingabe('max@exa')).toEqual({ fertig: [], rest: 'max@exa' });
        expect(teileEingabe('a@example.org, b@exa')).toEqual({ fertig: ['a@example.org'], rest: 'b@exa' });
        expect(teileEingabe('a@example.org;')).toEqual({ fertig: ['a@example.org'], rest: '' });
        expect(teileEingabe('"Mustermann, Max" <m')).toEqual({ fertig: [], rest: '"Mustermann, Max" <m' });
        expect(teileEingabe(',')).toEqual({ fertig: [], rest: '' });
    });

    it('nimmt keine Adresse doppelt auf und ignoriert Leeres', () => {
        const liste = fuegeEmpfaengerHinzu(['Max@Example.org'], [
            '"Max" <max@example.org>', '  ', 'erika@example.org', 'ERIKA@example.org',
        ]);
        expect(liste).toEqual(['Max@Example.org', 'erika@example.org']);
    });

    it('erkennt vorhandene Empfänger unabhängig von Name und Schreibweise', () => {
        expect(enthaeltEmpfaenger(['"Max" <max@example.org>'], 'MAX@example.org')).toBe(true);
        expect(enthaeltEmpfaenger(['max@example.org'], 'erika@example.org')).toBe(false);
    });

    it('zeigt auf dem Kärtchen den Namen, sonst die Adresse, und prüft die Adresse', () => {
        expect(kaertchenText('"Max Mustermann" <max@example.org>'))
            .toEqual({ anzeige: 'Max Mustermann', adresse: 'max@example.org', gueltig: true });
        expect(kaertchenText('erika@example.org'))
            .toEqual({ anzeige: 'erika@example.org', adresse: 'erika@example.org', gueltig: true });
        expect(kaertchenText('max@')).toMatchObject({ anzeige: 'max@', gueltig: false });
        expect(kaertchenText('""')).toEqual({ anzeige: '""', adresse: '""', gueltig: false });
    });
});
