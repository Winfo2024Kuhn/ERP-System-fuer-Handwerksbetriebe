import { describe, expect, it } from 'vitest';
import {
    escapeHtml, extractDisplayName, extractEmailAddress, formatRecipient, infoAdresseZuDomain, isSingleEmailAddress,
    istInfoAdresse, parseRecipientList, waehleInfoEmpfaenger,
} from './emailAddress';

describe('escapeHtml', () => {
    it('maskiert alle fuenf Sonderzeichen', () => {
        expect(escapeHtml(`<a href="x">Tom & 'Jerry'</a>`))
            .toBe('&lt;a href=&quot;x&quot;&gt;Tom &amp; &#039;Jerry&#039;&lt;/a&gt;');
    });
    it('verhindert Script-Injection in Zitatkoepfen', () => {
        const out = escapeHtml('Max <max@example.com><script>alert(1)</script>');
        expect(out).not.toContain('<');
        expect(out).toContain('&lt;max@example.com&gt;');
    });
    it('escaped & nur einmal (kein Doppel-Escaping bei Einzelaufruf)', () => {
        expect(escapeHtml('&lt;')).toBe('&amp;lt;');
    });
    it('liefert leeren String bei undefined/leer', () => {
        expect(escapeHtml()).toBe('');
        expect(escapeHtml('')).toBe('');
    });
});

describe('infoAdresseZuDomain / istInfoAdresse / waehleInfoEmpfaenger (Randfaelle)', () => {
    it('Adresse ohne @ oder mit @ am Anfang liefert keine Domain', () => {
        expect(infoAdresseZuDomain('keine-adresse')).toBe('');
        expect(infoAdresseZuDomain('@example.com')).toBe('');
        expect(infoAdresseZuDomain(undefined)).toBe('');
    });
    it('nutzt bei mehreren @ die letzte als Domain-Trenner', () => {
        expect(infoAdresseZuDomain('"a@b"@example.com')).toBe('info@example.com');
    });
    it('istInfoAdresse: undefined und Praefix-Faelle', () => {
        expect(istInfoAdresse(undefined)).toBe(false);
        expect(istInfoAdresse('Info@Example.com')).toBe(true);
        expect(istInfoAdresse('information@example.com')).toBe(false);
        expect(istInfoAdresse('Zentrale <info@example.com>')).toBe(true);
    });
    it('waehleInfoEmpfaenger ueberspringt undefined/leere Eintraege', () => {
        expect(waehleInfoEmpfaenger([undefined, '', '  ', 'bestellung@example.com'])).toBe('bestellung@example.com');
        expect(waehleInfoEmpfaenger([undefined, 'Zentrale <INFO@example.com>', 'a@example.com'])).toBe('INFO@example.com');
        expect(waehleInfoEmpfaenger(undefined)).toBe('');
    });
});

describe('parseRecipientList / Hilfsfunktionen (Randfaelle)', () => {
    it('leere Eingabe ergibt leere Liste', () => {
        expect(parseRecipientList()).toEqual([]);
        expect(parseRecipientList('')).toEqual([]);
    });
    it('trennt Adressen mit Semikolon', () => {
        const list = parseRecipientList('a@example.com; b@example.com');
        expect(list.map(r => r.email)).toEqual(['a@example.com', 'b@example.com']);
    });
    it('isSingleEmailAddress lehnt XSS-/SQL-Strings und leere Werte ab', () => {
        expect(isSingleEmailAddress(undefined)).toBe(false);
        expect(isSingleEmailAddress('')).toBe(false);
        expect(isSingleEmailAddress("'; DROP TABLE x; --")).toBe(false);
        expect(isSingleEmailAddress('<script>alert(1)</script>')).toBe(false);
    });
    it('extract*-Funktionen verarbeiten Sonderzeichen ohne Fehler', () => {
        expect(() => extractEmailAddress('<<<>>>')).not.toThrow();
        expect(() => extractDisplayName('"" <>')).not.toThrow();
        expect(() => formatRecipient('"Max" <max@example.com>', '<b>Evil</b>')).not.toThrow();
        expect(formatRecipient('max@example.com', 'Evil\r\nBcc: x@example.org')).not.toMatch(/[\r\n]/);
    });
});
