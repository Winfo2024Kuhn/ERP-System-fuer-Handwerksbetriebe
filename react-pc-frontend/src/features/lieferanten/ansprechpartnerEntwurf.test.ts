import { describe, expect, it } from 'vitest';
import {
    anzeigeName,
    entwurfAus,
    leererEntwurf,
    pruefeEntwurf,
    zuAnfrage,
    type LieferantAnsprechpartner,
} from './ansprechpartnerEntwurf';

const MAX: LieferantAnsprechpartner = {
    id: 4,
    anrede: 'HERR',
    vorname: 'Max',
    nachname: 'Mustermann',
    aufgabe: 'Außendienst',
    telefon: null,
    mobiltelefon: '0171 1234567',
    email: 'max@muster.de',
    notiz: null,
};

describe('anzeigeName', () => {
    it('setzt Anrede, Vorname und Nachname zusammen', () => {
        expect(anzeigeName(MAX)).toBe('Herr Max Mustermann');
        expect(anzeigeName({ ...MAX, anrede: 'FRAU', vorname: 'Erika', nachname: 'Musterfrau' })).toBe('Frau Erika Musterfrau');
    });

    it('lässt leere Teile und Anreden ohne Personenbezug weg', () => {
        expect(anzeigeName({ ...MAX, anrede: null, vorname: '  ' })).toBe('Mustermann');
        expect(anzeigeName({ ...MAX, anrede: 'DAMEN_HERREN' })).toBe('Max Mustermann');
        expect(anzeigeName({ ...MAX, anrede: 'FAMILIE' })).toBe('Familie Max Mustermann');
    });
});

describe('entwurfAus / leererEntwurf', () => {
    it('macht aus null-Feldern leere Texte', () => {
        expect(entwurfAus(MAX)).toEqual({
            anrede: 'HERR', vorname: 'Max', nachname: 'Mustermann', aufgabe: 'Außendienst',
            telefon: '', mobiltelefon: '0171 1234567', email: 'max@muster.de', notiz: '',
        });
        expect(Object.values(leererEntwurf()).every(wert => wert === '')).toBe(true);
    });
});

describe('pruefeEntwurf', () => {
    it('verlangt einen Nachnamen', () => {
        expect(pruefeEntwurf({ ...leererEntwurf(), nachname: '   ' })).toEqual({
            feld: 'nachname', meldung: 'Bitte einen Nachnamen eintragen.',
        });
    });

    it('prüft das E-Mail-Format nur, wenn eine Adresse eingetragen ist', () => {
        expect(pruefeEntwurf({ ...leererEntwurf(), nachname: 'Mustermann' })).toBeNull();
        expect(pruefeEntwurf({ ...leererEntwurf(), nachname: 'Mustermann', email: 'max@muster' })).toEqual({
            feld: 'email', meldung: 'Die E-Mail-Adresse sieht nicht richtig aus (Beispiel: name@firma.de).',
        });
        expect(pruefeEntwurf({ ...leererEntwurf(), nachname: 'Mustermann', email: ' max@muster.de ' })).toBeNull();
    });

    it('prüft die E-Mail so streng wie der Server', () => {
        for (const email of ['max@firma..de', 'max@firma.de.', 'a?cc=x@y.de', 'a&b@y.de', 'max@.de']) {
            expect(pruefeEntwurf({ ...leererEntwurf(), nachname: 'M', email })?.feld, email).toBe('email');
        }
        expect(pruefeEntwurf({ ...leererEntwurf(), nachname: 'M', email: 'max.mustermann@muster-stahl.example.de' })).toBeNull();
    });

    it('meldet zu lange Felder wie der Server', () => {
        expect(pruefeEntwurf({ ...leererEntwurf(), nachname: 'x'.repeat(256) })?.meldung).toBe('Der Nachname ist zu lang (höchstens 255 Zeichen).');
        expect(pruefeEntwurf({ ...leererEntwurf(), nachname: 'M', notiz: 'x'.repeat(1001) })?.feld).toBe('notiz');
    });
});

describe('zuAnfrage', () => {
    it('kürzt Leerzeichen und schickt leere Felder als null', () => {
        expect(zuAnfrage({ ...leererEntwurf(), nachname: ' Mustermann ', email: ' max@muster.de ', anrede: '' })).toEqual({
            anrede: null, vorname: null, nachname: 'Mustermann', aufgabe: null,
            telefon: null, mobiltelefon: null, email: 'max@muster.de', notiz: null,
        });
    });
});
