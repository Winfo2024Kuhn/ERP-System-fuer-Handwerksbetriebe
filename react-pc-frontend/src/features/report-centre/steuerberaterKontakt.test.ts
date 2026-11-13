import { describe, expect, it } from 'vitest';
import {
    adressenVon, ansprechpartnerFuerAdresse, lohnAnsprechpartner, type SteuerberaterAnsprechpartner, type SteuerberaterKontakt,
} from './steuerberaterKontakt';

const ap = (id: number, nachname: string, email: string, lohn = false): SteuerberaterAnsprechpartner => ({
    id, anrede: 'HERR', vorname: 'Max', nachname, email, telefon: '', istLohnAnsprechpartner: lohn,
});

const KANZLEI: SteuerberaterKontakt = {
    id: 1, name: 'Kanzlei Mustermann', email: 'kanzlei@example.com', telefon: '', weitereEmails: ['info@example.com', 'kanzlei@example.com'],
    ansprechpartnerListe: [ap(10, 'Mustermann', 'max@example.com'), ap(11, 'Lohnmann', 'lohn@example.com', true)],
};
const OHNE_ANSPRECHPARTNER: SteuerberaterKontakt = {
    id: 2, name: 'Kanzlei Musterfrau', email: 'b@example.com', telefon: '', weitereEmails: [], ansprechpartnerListe: [],
};

describe('lohnAnsprechpartner', () => {
    it('nimmt den Lohn-Ansprechpartner, sonst den ersten, sonst keinen', () => {
        expect(lohnAnsprechpartner(KANZLEI)?.id).toBe(11);
        expect(lohnAnsprechpartner({ ...KANZLEI, ansprechpartnerListe: [ap(10, 'Mustermann', 'max@example.com')] })?.id).toBe(10);
        expect(lohnAnsprechpartner(OHNE_ANSPRECHPARTNER)).toBeNull();
        expect(lohnAnsprechpartner(null)).toBeNull();
    });
});

describe('adressenVon', () => {
    it('sammelt Adressen ohne Doppelte und Leere', () => {
        expect(adressenVon(KANZLEI)).toEqual(['kanzlei@example.com', 'info@example.com', 'max@example.com', 'lohn@example.com']);
        expect(adressenVon(null)).toEqual([]);
    });
});

describe('ansprechpartnerFuerAdresse', () => {
    it('findet den Ansprechpartner zur Adresse, Groß-/Kleinschreibung und Leerzeichen egal', () => {
        expect(ansprechpartnerFuerAdresse(KANZLEI, 'max@example.com')?.id).toBe(10);
        expect(ansprechpartnerFuerAdresse(KANZLEI, '  Max@Example.COM ')?.id).toBe(10);
        expect(ansprechpartnerFuerAdresse(KANZLEI, 'lohn@example.com')?.id).toBe(11);
    });

    it('gibt bei Kanzlei-, weiterer oder fremder Adresse keinen zurück', () => {
        expect(ansprechpartnerFuerAdresse(KANZLEI, 'kanzlei@example.com')).toBeNull();
        expect(ansprechpartnerFuerAdresse(KANZLEI, 'info@example.com')).toBeNull();
        expect(ansprechpartnerFuerAdresse(KANZLEI, 'fremd@example.com')).toBeNull();
        expect(ansprechpartnerFuerAdresse(OHNE_ANSPRECHPARTNER, 'b@example.com')).toBeNull();
    });

    it('findet bei leerer Adresse oder ohne Steuerberater keinen, auch nicht über leere Ansprechpartner-Adressen', () => {
        const mitLeererAdresse = { ...KANZLEI, ansprechpartnerListe: [ap(12, 'Ohnemail', '')] };
        expect(ansprechpartnerFuerAdresse(mitLeererAdresse, '')).toBeNull();
        expect(ansprechpartnerFuerAdresse(mitLeererAdresse, '   ')).toBeNull();
        expect(ansprechpartnerFuerAdresse(null, 'max@example.com')).toBeNull();
    });

    it('nimmt bei geteilter Adresse den Lohn-Ansprechpartner, sonst den ersten', () => {
        const geteilt = { ...KANZLEI, ansprechpartnerListe: [ap(20, 'Erster', 'team@example.com'), ap(21, 'Lohn', 'TEAM@example.com', true), ap(22, 'Dritter', 'team@example.com')] };
        expect(ansprechpartnerFuerAdresse(geteilt, 'team@example.com')?.id).toBe(21);
        const ohneLohn = { ...KANZLEI, ansprechpartnerListe: [ap(20, 'Erster', 'team@example.com'), ap(22, 'Dritter', 'team@example.com')] };
        expect(ansprechpartnerFuerAdresse(ohneLohn, 'team@example.com')?.id).toBe(20);
    });
});
