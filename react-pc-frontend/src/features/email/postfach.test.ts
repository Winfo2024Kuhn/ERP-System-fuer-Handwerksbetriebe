import { describe, expect, it } from 'vitest';
import {
    beschreibeAbruf, beschreibeSichtbarkeit, formatPostfach, immerFuerAlleSichtbar, formatVorZeit, lokalerTeil, parseAbsenderPostfaecher,
    parsePostfach, parsePostfaecher, parseSichtbarkeitsAbteilungen, parseSichtbarkeitsBenutzer, passtZurSuche,
    sichtbarkeitAusPostfach, sichtbarkeitGeaendert,
    waehleStandardAbsender, type AbsenderPostfach, type PostfachDto,
} from './postfach';

const absender = (teil: Partial<AbsenderPostfach> & { id: number }): AbsenderPostfach => ({
    emailAdresse: `p${teil.id}@musterbetrieb.example`, anzeigename: null, eigenes: false, hauptpostfach: false, ...teil,
});

const postfach = (teil: Partial<PostfachDto> = {}): PostfachDto => ({
    id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb', aktiv: true, sortierung: 0,
    hauptpostfach: true, fuerGeschaeftsdokumente: false, benutzername: 'info@musterbetrieb.example', passwortGesetzt: true,
    smtpHost: 'mail.your-server.de', smtpPort: 465, imapHost: 'mail.your-server.de', imapPort: 993, abrufAktiv: true,
    letzterAbrufAm: '2026-10-10T09:41:00', letzterAbrufFehler: null, zugewieseneBenutzer: [],
    sichtbarFuerAlle: true, sichtbarFuerAbteilungen: [], sichtbarFuerBenutzer: [], laeuftAus: false, ...teil,
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

describe('postfach – Sichtbarkeit', () => {
    const nurBestimmte = (teil: Partial<PostfachDto> = {}) => postfach({ hauptpostfach: false, sichtbarFuerAlle: false, ...teil });

    it('zeigt „alle“ beim Hauptpostfach und bei „Alle im Betrieb“', () => {
        expect(beschreibeSichtbarkeit(postfach())).toEqual({ text: 'alle', weitere: 0, vollstaendig: 'Jeder im Betrieb sieht dieses Postfach.' });
        // Hauptpostfach sieht immer jeder – auch wenn das Backend (fälschlich) etwas anderes liefert.
        expect(beschreibeSichtbarkeit(postfach({ sichtbarFuerAlle: false })).text).toBe('alle');
        expect(beschreibeSichtbarkeit(postfach({ hauptpostfach: false })).text).toBe('alle');
        // Ältere Antwort ohne das Feld: Standard „für alle“.
        expect(beschreibeSichtbarkeit(postfach({ hauptpostfach: false, sichtbarFuerAlle: undefined as unknown as boolean })).text).toBe('alle');
    });

    it('zeigt beim Postfach für Rechnungen & Mahnungen immer „alle“', () => {
        const rechnungen = nurBestimmte({
            fuerGeschaeftsdokumente: true, sichtbarFuerAbteilungen: [{ id: 2, name: 'Büro' }],
        });
        expect(beschreibeSichtbarkeit(rechnungen).text).toBe('alle');
        expect(immerFuerAlleSichtbar(rechnungen)).toBe(true);
        expect(immerFuerAlleSichtbar(postfach())).toBe(true);
        expect(immerFuerAlleSichtbar(nurBestimmte())).toBe(false);
    });

    it('nennt Abteilungen vor Benutzern', () => {
        expect(beschreibeSichtbarkeit(nurBestimmte({
            sichtbarFuerAbteilungen: [{ id: 2, name: 'Büro' }],
            sichtbarFuerBenutzer: [{ id: 7, displayName: 'Max Mustermann' }],
        }))).toEqual({ text: 'Büro, Max Mustermann', weitere: 0, vollstaendig: 'Büro, Max Mustermann' });
    });

    it('kürzt lange Aufzählungen mit „+N“ und hält die volle Liste für den Tooltip', () => {
        const anzeige = beschreibeSichtbarkeit(nurBestimmte({
            sichtbarFuerAbteilungen: [{ id: 2, name: 'Büro' }, { id: 3, name: 'Werkstatt' }],
            sichtbarFuerBenutzer: [
                { id: 7, displayName: 'Max Mustermann' }, { id: 8, displayName: 'Erika Musterfrau' },
                { id: 9, displayName: 'Moritz Muster' },
            ],
        }));
        expect(anzeige.text).toBe('Büro, Werkstatt, Max Mustermann');
        expect(anzeige.weitere).toBe(2);
        expect(anzeige.vollstaendig).toBe('Büro, Werkstatt, Max Mustermann, Erika Musterfrau, Moritz Muster');
    });

    it('sagt klar, wenn nichts angehakt ist (auch bei fehlenden Listen)', () => {
        const anzeige = beschreibeSichtbarkeit(nurBestimmte({
            sichtbarFuerAbteilungen: undefined as unknown as [], sichtbarFuerBenutzer: undefined as unknown as [],
        }));
        expect(anzeige.text).toBe('nur Inhaber und Admins');
        expect(anzeige.weitere).toBe(0);
        expect(anzeige.vollstaendig).toMatch(/nur der Inhaber und Admins/);
    });

    it('liest Abteilungen aus /api/abteilungen/berechtigungen robust und sortiert ein', () => {
        expect(parseSichtbarkeitsAbteilungen({ kaputt: true })).toEqual([]);
        expect(parseSichtbarkeitsAbteilungen([
            { abteilungId: 3, abteilungName: 'Werkstatt', berechtigungen: [] },
            null,
            { abteilungId: 'x', abteilungName: 'Falsch' },
            { abteilungId: 4 },
            { abteilungId: 2, abteilungName: 'Büro' },
        ])).toEqual([{ id: 2, name: 'Büro' }, { id: 3, name: 'Werkstatt' }]);
    });

    it('liest Benutzer aus /api/frontend-users robust, sortiert ein und merkt sich „aktiv“', () => {
        expect(parseSichtbarkeitsBenutzer('nein')).toEqual([]);
        expect(parseSichtbarkeitsBenutzer([
            { id: 8, displayName: 'Moritz Muster', active: false },
            { id: 7, displayName: 'Max Mustermann', active: true },
            { id: 9, displayName: 'Erika Musterfrau' },
            { id: 10 },
            'Unsinn',
        ])).toEqual([
            { id: 9, displayName: 'Erika Musterfrau', aktiv: true },
            { id: 7, displayName: 'Max Mustermann', aktiv: true },
            { id: 8, displayName: 'Moritz Muster', aktiv: false },
        ]);
    });

    it('sucht ohne Rücksicht auf Groß/Klein und Leerzeichen', () => {
        expect(passtZurSuche('Max Mustermann', '')).toBe(true);
        expect(passtZurSuche('Max Mustermann', '  ')).toBe(true);
        expect(passtZurSuche('Max Mustermann', ' muster ')).toBe(true);
        expect(passtZurSuche('Büro', 'BÜ')).toBe(true);
        expect(passtZurSuche('Werkstatt', 'büro')).toBe(false);
    });
});

describe('postfach – Sichtbarkeit bearbeiten', () => {
    const bestimmte = postfach({
        hauptpostfach: false, sichtbarFuerAlle: false,
        sichtbarFuerAbteilungen: [{ id: 2, name: 'Büro' }, { id: 3, name: 'Werkstatt' }],
        sichtbarFuerBenutzer: [{ id: 7, displayName: 'Max Mustermann' }],
    });

    it('macht aus dem gespeicherten Postfach die Ausgangs-Auswahl', () => {
        expect(sichtbarkeitAusPostfach(bestimmte)).toEqual({ sichtbarFuerAlle: false, abteilungIds: [2, 3], benutzerIds: [7] });
        expect(sichtbarkeitAusPostfach(postfach({
            sichtbarFuerAlle: undefined as unknown as boolean,
            sichtbarFuerAbteilungen: undefined as unknown as [], sichtbarFuerBenutzer: undefined as unknown as [],
        }))).toEqual({ sichtbarFuerAlle: true, abteilungIds: [], benutzerIds: [] });
    });

    it('erkennt Änderungen, aber nicht die Reihenfolge der Häkchen', () => {
        expect(sichtbarkeitGeaendert(bestimmte, { sichtbarFuerAlle: false, abteilungIds: [3, 2], benutzerIds: [7] })).toBe(false);
        expect(sichtbarkeitGeaendert(bestimmte, { sichtbarFuerAlle: true, abteilungIds: [2, 3], benutzerIds: [7] })).toBe(true);
        expect(sichtbarkeitGeaendert(bestimmte, { sichtbarFuerAlle: false, abteilungIds: [2], benutzerIds: [7] })).toBe(true);
        expect(sichtbarkeitGeaendert(bestimmte, { sichtbarFuerAlle: false, abteilungIds: [2, 4], benutzerIds: [7] })).toBe(true);
        expect(sichtbarkeitGeaendert(bestimmte, { sichtbarFuerAlle: false, abteilungIds: [2, 3], benutzerIds: [8] })).toBe(true);
    });

    it('liest ein einzelnes Postfach robust ein', () => {
        expect(parsePostfach(postfach())).toEqual(postfach());
        expect(parsePostfach(null)).toBeNull();
        expect(parsePostfach('Unsinn')).toBeNull();
        expect(parsePostfach({ id: '3', emailAdresse: 'info@musterbetrieb.example' })).toBeNull();
        expect(parsePostfach({ id: 3 })).toBeNull();
    });

    it('nimmt bei fehlendem „laeuftAus“ false an', () => {
        const { laeuftAus: _ohne, ...alt } = postfach();
        void _ohne;
        expect(parsePostfach(alt)?.laeuftAus).toBe(false);
        expect(parsePostfach({ ...alt, laeuftAus: 'ja' })?.laeuftAus).toBe(false);
        expect(parsePostfach(postfach({ laeuftAus: true }))?.laeuftAus).toBe(true);
        expect(parsePostfaecher([alt, null]).map(p => p.laeuftAus)).toEqual([false]);
    });

    it('liest die Postfach-Liste robust ein', () => {
        expect(parsePostfaecher(null)).toEqual([]);
        expect(parsePostfaecher([postfach(), null, { id: 'x', emailAdresse: 'a@example.org' }, { id: 4 }]))
            .toEqual([postfach()]);
    });
});
