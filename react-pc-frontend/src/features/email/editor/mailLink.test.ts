import { describe, expect, it } from 'vitest';
import {
    LINK_ADRESSE_UNGUELTIG, anzeigeAdresse, istErlaubterLink, kurzeAdresse, linkText, normalisiereLinkAdresse,
    webAdresseAusEinfuegen,
} from './mailLink';

describe('normalisiereLinkAdresse', () => {
    it('lässt https-Adressen wie OneDrive-Freigaben stehen', () => {
        const adresse = 'https://onedrive.example.com/f/s!AbCdEf123?e=XyZ';
        expect(normalisiereLinkAdresse(adresse)).toEqual({ ok: true, href: adresse });
    });

    it('stellt einer Adresse ohne Schema https:// voran', () => {
        expect(normalisiereLinkAdresse('  www.example.com/angebot ')).toEqual({
            ok: true, href: 'https://www.example.com/angebot',
        });
    });

    it('behandelt eine Rechneradresse mit Port nicht als Schema', () => {
        expect(normalisiereLinkAdresse('localhost:8080/x')).toEqual({ ok: true, href: 'https://localhost:8080/x' });
    });

    it('macht aus einer E-Mail-Adresse einen mailto-Link', () => {
        expect(normalisiereLinkAdresse('max.mustermann@example.com')).toEqual({ ok: true, href: 'mailto:max.mustermann@example.com' });
        expect(normalisiereLinkAdresse('mailto:max@example.com')).toEqual({ ok: true, href: 'mailto:max@example.com' });
    });

    it('erlaubt http', () => {
        expect(normalisiereLinkAdresse('http://example.com')).toEqual({ ok: true, href: 'http://example.com' });
    });

    it.each([
        '',
        '   ',
        'javascript:alert(1)',
        'JavaScript:alert(1)',
        ' javascript:alert(1)',
        'data:text/html,<script>alert(1)</script>',
        'file:///C:/geheim.txt',
        'vbscript:msgbox(1)',
        'ftp://example.com',
        'mailto:',
        'https://',
        'https://example .com',
        'example .com',
        '//example.com',
    ])('lehnt %j ab', (eingabe) => {
        expect(normalisiereLinkAdresse(eingabe)).toEqual({ ok: false, meldung: LINK_ADRESSE_UNGUELTIG });
    });
});

describe('istErlaubterLink', () => {
    it('erlaubt nur http, https und mailto', () => {
        expect(istErlaubterLink('https://example.com')).toBe(true);
        expect(istErlaubterLink('http://example.com')).toBe(true);
        expect(istErlaubterLink('mailto:max@example.com')).toBe(true);
        expect(istErlaubterLink('javascript:alert(1)')).toBe(false);
        expect(istErlaubterLink('data:text/html,x')).toBe(false);
        expect(istErlaubterLink('/relativ')).toBe(false);
        expect(istErlaubterLink(null)).toBe(false);
        expect(istErlaubterLink('kaputt')).toBe(false);
    });
});

describe('anzeigeAdresse und kurzeAdresse', () => {
    it('zeigt Adressen ohne Schema', () => {
        expect(anzeigeAdresse('https://example.com/datei')).toBe('example.com/datei');
        expect(anzeigeAdresse('mailto:max@example.com')).toBe('max@example.com');
        expect(anzeigeAdresse('http://example.com')).toBe('example.com');
    });

    it('kürzt lange Adressen mit Auslassungszeichen', () => {
        const lang = `https://onedrive.example.com/${'a'.repeat(80)}`;
        const kurz = kurzeAdresse(lang, 30);
        expect(kurz).toHaveLength(30);
        expect(kurz.endsWith('…')).toBe(true);
        expect(kurzeAdresse('https://kurz.example.com', 30)).toBe('kurz.example.com');
    });
});

describe('linkText', () => {
    it('nimmt den eingetragenen Text', () => {
        expect(linkText('  Hier klicken ', 'https://example.com')).toBe('Hier klicken');
    });

    it('nimmt ohne Text die Adresse', () => {
        expect(linkText('', 'https://example.com/x')).toBe('https://example.com/x');
        expect(linkText('  ', 'mailto:max@example.com')).toBe('max@example.com');
    });

    it('lässt HTML im Text als reinen Text stehen', () => {
        expect(linkText('<img src=x onerror=alert(1)>', 'https://example.com')).toBe('<img src=x onerror=alert(1)>');
    });
});

describe('webAdresseAusEinfuegen', () => {
    it('erkennt eine einzelne Web-Adresse', () => {
        expect(webAdresseAusEinfuegen(' https://onedrive.example.com/f/s!Abc \n')).toBe('https://onedrive.example.com/f/s!Abc');
        expect(webAdresseAusEinfuegen('http://example.com')).toBe('http://example.com');
    });

    it('ignoriert alles andere', () => {
        expect(webAdresseAusEinfuegen('Hallo Welt')).toBeNull();
        expect(webAdresseAusEinfuegen('https://a.example.com https://b.example.com')).toBeNull();
        expect(webAdresseAusEinfuegen('www.example.com')).toBeNull();
        expect(webAdresseAusEinfuegen('max@example.com')).toBeNull();
        expect(webAdresseAusEinfuegen('javascript:alert(1)')).toBeNull();
        expect(webAdresseAusEinfuegen('')).toBeNull();
    });
});
