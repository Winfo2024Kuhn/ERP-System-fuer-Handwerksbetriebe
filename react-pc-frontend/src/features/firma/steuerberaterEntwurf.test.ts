import { describe, expect, it } from 'vitest';
import {
    ansprechpartnerAendern, ansprechpartnerEntfernen, ansprechpartnerHinzufuegen, lohnAnsprechpartnerSetzen,
    kanzleiHakenGreiftNicht, neuerSteuerberater, pruefeSteuerberaterEntwurf, weitereEmailAendern, weitereEmailEntfernen, weitereEmailHinzufuegen,
} from './steuerberaterEntwurf';
import type { SteuerberaterEntwurf } from './firmaTypen';

const ap = (nachname: string, istLohnAnsprechpartner = false) => ({
    anrede: 'HERR', vorname: 'Max', nachname, email: '', telefon: '', istLohnAnsprechpartner,
});

describe('neuerSteuerberater', () => {
    it('startet aktiv, mit automatischer Verarbeitung und heutigem Datum', () => {
        expect(neuerSteuerberater(new Date('2026-11-08T10:00:00Z')))
            .toEqual({ aktiv: true, autoProcessEmails: true, gueltigAb: '2026-11-08' });
    });

    it('nimmt kurz nach Mitternacht das heutige Ortsdatum, nicht den UTC-Vortag', () => {
        expect(neuerSteuerberater(new Date(2026, 2, 1, 0, 30)).gueltigAb).toBe('2026-03-01');
    });
});

describe('weitere E-Mail-Adressen', () => {
    it('hängt eine leere Adresse an, auch ohne bisherige Liste', () => {
        expect(weitereEmailHinzufuegen(null)).toEqual({ weitereEmails: [''] });
        expect(weitereEmailHinzufuegen({ name: 'Kanzlei', weitereEmails: ['a@example.org'] }))
            .toEqual({ name: 'Kanzlei', weitereEmails: ['a@example.org', ''] });
    });

    it('ändert und entfernt genau die gewählte Adresse', () => {
        const entwurf: SteuerberaterEntwurf = { weitereEmails: ['a@example.org', 'b@example.org'] };
        expect(weitereEmailAendern(entwurf, 1, 'c@example.org').weitereEmails).toEqual(['a@example.org', 'c@example.org']);
        expect(weitereEmailEntfernen(entwurf, 0).weitereEmails).toEqual(['b@example.org']);
        expect(entwurf.weitereEmails).toEqual(['a@example.org', 'b@example.org']);
    });
});

describe('Ansprechpartner', () => {
    it('macht den ersten Ansprechpartner zum Lohn-Ansprechpartner, weitere nicht', () => {
        const erster = ansprechpartnerHinzufuegen(null);
        expect(erster.ansprechpartnerListe).toEqual([{
            anrede: 'HERR', vorname: '', nachname: '', email: '', telefon: '', istLohnAnsprechpartner: true,
        }]);
        const zweiter = ansprechpartnerHinzufuegen(erster);
        expect(zweiter.ansprechpartnerListe?.[1].istLohnAnsprechpartner).toBe(false);
    });

    it('ändert Felder eines Ansprechpartners, ohne die anderen anzufassen', () => {
        const entwurf: SteuerberaterEntwurf = { ansprechpartnerListe: [ap('Mustermann'), ap('Musterfrau')] };
        const neu = ansprechpartnerAendern(entwurf, 1, { anrede: null });
        expect(neu.ansprechpartnerListe?.[1]).toEqual({ ...ap('Musterfrau'), anrede: null });
        expect(neu.ansprechpartnerListe?.[0]).toBe(entwurf.ansprechpartnerListe?.[0]);
    });

    it('entfernt einen Ansprechpartner und setzt genau einen Lohn-Ansprechpartner', () => {
        const entwurf: SteuerberaterEntwurf = { ansprechpartnerListe: [ap('Mustermann', true), ap('Musterfrau')] };
        expect(ansprechpartnerEntfernen(entwurf, 0).ansprechpartnerListe).toEqual([ap('Musterfrau')]);
        expect(lohnAnsprechpartnerSetzen(entwurf, 1).ansprechpartnerListe?.map(a => a.istLohnAnsprechpartner)).toEqual([false, true]);
    });
});

describe('pruefeSteuerberaterEntwurf', () => {
    it('verlangt einen Namen', () => {
        expect(pruefeSteuerberaterEntwurf(null)).toBe('Bitte Namen eintragen.');
        expect(pruefeSteuerberaterEntwurf({ name: '   ', email: 'kanzlei@example.org' })).toBe('Bitte Namen eintragen.');
    });

    it('verlangt eine Haupt-E-Mail', () => {
        expect(pruefeSteuerberaterEntwurf({ name: 'Kanzlei Mustermann' })).toBe('Bitte Haupt-E-Mail eintragen.');
        expect(pruefeSteuerberaterEntwurf({ name: 'Kanzlei Mustermann', email: ' ' })).toBe('Bitte Haupt-E-Mail eintragen.');
    });

    it('lässt einen vollständigen Entwurf durch, auch ohne „Gültig ab“', () => {
        expect(pruefeSteuerberaterEntwurf({ name: 'Kanzlei Mustermann', email: 'kanzlei@example.org', gueltigAb: '' })).toBeNull();
    });

    const FORMAT = 'Die Haupt-E-Mail sieht nicht richtig aus, bitte prüfen (Beispiel: name@kanzlei.de).';

    it.each(['max', 'max@', 'a@b.de, c@d.de', '"Max" <max@example.com>', 'max mustermann@example.com'])(
        'lehnt die Haupt-E-Mail „%s“ mit klarer Meldung ab',
        email => {
            expect(pruefeSteuerberaterEntwurf({ name: 'Kanzlei Mustermann', email })).toBe(FORMAT);
        },
    );

    it('nimmt eine einzelne Adresse an, auch mit Leerzeichen am Rand', () => {
        expect(pruefeSteuerberaterEntwurf({ name: 'Kanzlei Mustermann', email: 'max.mustermann@example.com' })).toBeNull();
        expect(pruefeSteuerberaterEntwurf({ name: 'Kanzlei Mustermann', email: ' max.mustermann@example.com ' })).toBeNull();
    });

    it('prüft ausgefüllte weitere E-Mails und nennt die Nummer, leere Zeilen stören nicht', () => {
        const basis = { name: 'Kanzlei Mustermann', email: 'kanzlei@example.org' };
        expect(pruefeSteuerberaterEntwurf({ ...basis, weitereEmails: ['lohn@example.org', 'max@'] }))
            .toBe('Die weitere E-Mail-Adresse 2 sieht nicht richtig aus, bitte prüfen (Beispiel: name@kanzlei.de).');
        expect(pruefeSteuerberaterEntwurf({ ...basis, weitereEmails: ['', ' ', 'lohn@example.org'] })).toBeNull();
    });
});

describe('kanzleiHakenGreiftNicht', () => {
    it('meldet Freemail-Adressen nur bei gesetztem Haken', () => {
        expect(kanzleiHakenGreiftNicht({ email: 'max.mustermann@GMX.de', autoProcessEmails: true })).toBe(true);
        expect(kanzleiHakenGreiftNicht({ email: 'max.mustermann@gmx.de', autoProcessEmails: false })).toBe(false);
    });

    it('meldet nichts bei Kanzlei-Domain, leerer oder unfertiger Adresse', () => {
        expect(kanzleiHakenGreiftNicht({ email: 'kanzlei@example.org', autoProcessEmails: true })).toBe(false);
        expect(kanzleiHakenGreiftNicht({ email: '', autoProcessEmails: true })).toBe(false);
        expect(kanzleiHakenGreiftNicht({ email: 'max', autoProcessEmails: true })).toBe(false);
        expect(kanzleiHakenGreiftNicht(null)).toBe(false);
    });

    it('gilt auch, wenn der Haken nie angefasst wurde (Vorgabe: an)', () => {
        expect(kanzleiHakenGreiftNicht({ email: 'max.mustermann@web.de' })).toBe(true);
    });
});
