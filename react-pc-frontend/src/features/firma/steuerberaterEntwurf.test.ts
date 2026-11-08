import { describe, expect, it } from 'vitest';
import {
    ansprechpartnerAendern, ansprechpartnerEntfernen, ansprechpartnerHinzufuegen, lohnAnsprechpartnerSetzen,
    neuerSteuerberater, weitereEmailAendern, weitereEmailEntfernen, weitereEmailHinzufuegen,
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
