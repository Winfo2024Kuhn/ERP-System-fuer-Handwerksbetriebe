import { describe, expect, it } from 'vitest';
import {
    beschreibeAbruf, formatPostfach, formatVorZeit, lokalerTeil, parseAbsenderPostfaecher,
    waehleStandardAbsender, type AbsenderPostfach, type PostfachDto,
} from './postfach';

const absender = (teil: Partial<AbsenderPostfach> & { id: number }): AbsenderPostfach => ({
    emailAdresse: `p${teil.id}@musterbetrieb.example`, anzeigename: null, eigenes: false, hauptpostfach: false, ...teil,
});

const postfach = (teil: Partial<PostfachDto> = {}): PostfachDto => ({
    id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb', aktiv: true, sortierung: 0,
    hauptpostfach: true, fuerGeschaeftsdokumente: false, benutzername: 'info@musterbetrieb.example', passwortGesetzt: true,
    smtpHost: 'mail.your-server.de', smtpPort: 465, imapHost: 'mail.your-server.de', imapPort: 993, abrufAktiv: true,
    letzterAbrufAm: '2026-10-10T09:41:00', letzterAbrufFehler: null, zugewieseneBenutzer: [], ...teil,
});

describe('postfach', () => {
    it('kürzt die Adresse auf den lokalen Teil mit @', () => {
        expect(lokalerTeil('info@musterbetrieb.example')).toBe('info@');
        expect(lokalerTeil('ohne-at')).toBe('ohne-at');
        expect(lokalerTeil('@domain.de')).toBe('@domain.de');
    });

    it('formatiert Postfächer mit und ohne Anzeigenamen', () => {
        expect(formatPostfach({ id: 1, emailAdresse: 'info@example.org', anzeigename: 'Max Mustermann' }))
            .toBe('Max Mustermann <info@example.org>');
        expect(formatPostfach({ id: 1, emailAdresse: 'info@example.org', anzeigename: '  ' })).toBe('info@example.org');
        expect(formatPostfach({ id: 1, emailAdresse: 'info@example.org', anzeigename: null })).toBe('info@example.org');
    });

    it('belegt den Absender mit eigenem, sonst Haupt-, sonst erstem Postfach vor', () => {
        expect(waehleStandardAbsender([absender({ id: 1 }), absender({ id: 2, hauptpostfach: true }), absender({ id: 3, eigenes: true })])?.id).toBe(3);
        expect(waehleStandardAbsender([absender({ id: 1 }), absender({ id: 2, hauptpostfach: true })])?.id).toBe(2);
        expect(waehleStandardAbsender([absender({ id: 5 }), absender({ id: 6 })])?.id).toBe(5);
        expect(waehleStandardAbsender([])).toBeNull();
    });

    it('liest die Absender-Liste robust ein', () => {
        expect(parseAbsenderPostfaecher(null)).toEqual([]);
        expect(parseAbsenderPostfaecher([null, { id: 'x' }, { id: 1, emailAdresse: 'a@example.org' }]))
            .toEqual([{ id: 1, emailAdresse: 'a@example.org' }]);
    });

    it('beschreibt die vergangene Zeit in Handwerker-Sprache', () => {
        const jetzt = new Date('2026-10-10T12:00:00');
        expect(formatVorZeit('2026-10-10T11:59:40', jetzt)).toBe('gerade eben');
        expect(formatVorZeit('2026-10-10T11:58:00', jetzt)).toBe('vor 2 Min.');
        expect(formatVorZeit('2026-10-10T09:00:00', jetzt)).toBe('vor 3 Std.');
        expect(formatVorZeit('2026-10-09T10:00:00', jetzt)).toBe('vor 1 Tag');
        expect(formatVorZeit('2026-10-07T10:00:00', jetzt)).toBe('vor 3 Tagen');
        expect(formatVorZeit('kein Datum', jetzt)).toBe('');
    });

    it('fasst den Abruf-Status je Postfach zusammen', () => {
        const jetzt = new Date('2026-10-10T09:43:00');
        expect(beschreibeAbruf(postfach(), jetzt)).toEqual({ art: 'ok', text: 'Abruf ok · vor 2 Min.' });
        expect(beschreibeAbruf(postfach({ letzterAbrufAm: 'kaputt' }), jetzt)).toEqual({ art: 'ok', text: 'Abruf ok' });
        expect(beschreibeAbruf(postfach({ letzterAbrufFehler: 'Anmeldung fehlgeschlagen – Passwort prüfen' }), jetzt))
            .toEqual({ art: 'fehler', text: 'Anmeldung fehlgeschlagen – Passwort prüfen' });
        expect(beschreibeAbruf(postfach({ aktiv: false }), jetzt).art).toBe('aus');
        expect(beschreibeAbruf(postfach({ abrufAktiv: false }), jetzt).art).toBe('unvollstaendig');
        expect(beschreibeAbruf(postfach({ letzterAbrufAm: null }), jetzt)).toEqual({ art: 'wartet', text: 'Noch nicht abgerufen' });
        expect(beschreibeAbruf(postfach({ letzterAbrufAm: new Date().toISOString() })).art).toBe('ok');
    });
});
