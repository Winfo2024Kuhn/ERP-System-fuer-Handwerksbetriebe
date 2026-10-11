import { describe, expect, it } from 'vitest';
import {
    bildLinkAufbereiten,
    dataUriBytes,
    escapeHtml,
    istErlaubtesBildDataUri,
    istHexFarbe,
    kontrastFarbe,
    mailtoHref,
    MAX_BILD_BYTES,
    sichereBildUrl,
    sichereFarbe,
    sichereLinkUrl,
    sozialUrl,
    telHref,
    websiteAnzeige,
    whatsappUrl,
} from './signaturSicherheit';

const PIXEL_PNG = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';

describe('escapeHtml', () => {
    it('escaped alle gefährlichen Zeichen', () => {
        expect(escapeHtml(`<script>alert("x")</script> & 'y'`))
            .toBe('&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt; &amp; &#39;y&#39;');
    });
    it('leer bei null/undefined', () => {
        expect(escapeHtml(null)).toBe('');
        expect(escapeHtml(undefined)).toBe('');
    });
});

describe('sichereLinkUrl', () => {
    it('lässt http und https durch', () => {
        expect(sichereLinkUrl('https://www.example.com/seite')).toBe('https://www.example.com/seite');
        expect(sichereLinkUrl('http://example.com')).toBe('http://example.com/');
    });
    it('ergänzt https bei Eingaben ohne Schema', () => {
        expect(sichereLinkUrl('www.mustermann-metallbau.de')).toBe('https://www.mustermann-metallbau.de/');
    });
    it('verwirft javascript:, data:, mailto:, relative und protokoll-relative Links', () => {
        for (const boese of ['javascript:alert(1)', 'JavaScript:alert(1)', 'data:text/html,<b>x</b>', 'mailto:max@example.com', '/intern', '//boese.example.com', '\\\\server\\x', '']) {
            expect(sichereLinkUrl(boese)).toBe('');
        }
    });
});

describe('Data-URIs für Bilder', () => {
    it('erlaubt png/jpeg/gif/webp als base64', () => {
        expect(istErlaubtesBildDataUri(PIXEL_PNG)).toBe(true);
        for (const typ of ['jpeg', 'gif', 'webp']) {
            expect(istErlaubtesBildDataUri(`data:image/${typ};base64,AAAA`)).toBe(true);
        }
    });
    it('verbietet SVG, HTML, nicht-base64 und kaputte Daten', () => {
        expect(istErlaubtesBildDataUri('data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=')).toBe(false);
        expect(istErlaubtesBildDataUri('data:text/html;base64,PGI+eDwvYj4=')).toBe(false);
        expect(istErlaubtesBildDataUri('data:image/png,rohdaten')).toBe(false);
        expect(istErlaubtesBildDataUri('data:image/png;base64,AA"onerror="x')).toBe(false);
        expect(istErlaubtesBildDataUri('data:image/png;base64,')).toBe(false);
        expect(istErlaubtesBildDataUri('data:image/png;base64')).toBe(false);
        expect(istErlaubtesBildDataUri(null)).toBe(false);
    });
    it('verbietet Bilder über 1 MB', () => {
        const zuGross = `data:image/png;base64,${'A'.repeat(Math.ceil((MAX_BILD_BYTES + 10) / 3) * 4)}`;
        expect(istErlaubtesBildDataUri(zuGross)).toBe(false);
    });
    it('berechnet die Größe aus dem Base64-Text', () => {
        expect(dataUriBytes('data:image/png;base64,AAAA')).toBe(3);
        expect(dataUriBytes('data:image/png;base64,AAA=')).toBe(2);
        expect(dataUriBytes('data:image/png;base64,AA==')).toBe(1);
        expect(dataUriBytes('ohne-komma')).toBe(0);
    });
});

describe('sichereBildUrl', () => {
    it('Links und erlaubte Data-URIs', () => {
        expect(sichereBildUrl('https://example.com/logo.png')).toBe('https://example.com/logo.png');
        expect(sichereBildUrl(PIXEL_PNG)).toBe(PIXEL_PNG);
    });
    it('verwirft alles andere, auch Links ohne Schema', () => {
        expect(sichereBildUrl('javascript:alert(1)')).toBe('');
        expect(sichereBildUrl('data:image/svg+xml;base64,PHN2Zz4=')).toBe('');
        expect(sichereBildUrl('www.example.com/logo.png')).toBe('');
        expect(sichereBildUrl('/api/datei/1')).toBe('');
        expect(sichereBildUrl('')).toBe('');
    });
});

describe('bildLinkAufbereiten', () => {
    it('macht aus Dropbox-Freigabelinks direkte Bildlinks', () => {
        expect(bildLinkAufbereiten('https://www.dropbox.com/scl/fi/abc/logo.png?rlkey=xyz&dl=0'))
            .toBe('https://www.dropbox.com/scl/fi/abc/logo.png?rlkey=xyz&raw=1');
        expect(bildLinkAufbereiten('https://dropbox.com/s/abc/logo.png')).toBe('https://dropbox.com/s/abc/logo.png?raw=1');
    });
    it('lässt andere und fertige Links unverändert', () => {
        expect(bildLinkAufbereiten('https://www.dropbox.com/s/a.png?raw=1')).toBe('https://www.dropbox.com/s/a.png?raw=1');
        expect(bildLinkAufbereiten('https://example.com/dropbox.com.png')).toBe('https://example.com/dropbox.com.png');
        expect(bildLinkAufbereiten('https://evil-dropbox.com/a.png')).toBe('https://evil-dropbox.com/a.png');
        expect(bildLinkAufbereiten(' http://www.dropbox.com/a.png ')).toBe('http://www.dropbox.com/a.png');
        expect(bildLinkAufbereiten('https://[kaputt')).toBe('https://[kaputt');
    });
});

describe('Telefon, E-Mail, WhatsApp, soziale Netzwerke', () => {
    it('telHref behält nur Ziffern und ein führendes +', () => {
        expect(telHref('+49 (931) 123-456')).toBe('tel:+49931123456');
        expect(telHref('0931 / 123456')).toBe('tel:0931123456');
        expect(telHref('keine Nummer')).toBe('');
    });
    it('mailtoHref prüft die Adresse', () => {
        expect(mailtoHref('max@example.com')).toBe('mailto:max@example.com');
        expect(mailtoHref('max@example')).toBe('');
        expect(mailtoHref('max"onmouseover="x@example.com')).toBe('');
        expect(mailtoHref('a@b@example.com')).toBe('');
        expect(mailtoHref('@example.com')).toBe('');
        expect(mailtoHref('max@.example.com')).toBe('');
        expect(mailtoHref('max@example.com.')).toBe('');
    });
    it('whatsappUrl macht aus deutschen Nummern wa.me-Links', () => {
        expect(whatsappUrl('0171 1234567')).toBe('https://wa.me/491711234567');
        expect(whatsappUrl('+49 171 1234567')).toBe('https://wa.me/491711234567');
        expect(whatsappUrl('0049 171 1234567')).toBe('https://wa.me/491711234567');
        expect(whatsappUrl('https://wa.me/491711234567')).toBe('https://wa.me/491711234567');
        expect(whatsappUrl('javascript:alert(1)')).toBe('');
        expect(whatsappUrl('abc')).toBe('');
        expect(whatsappUrl('')).toBe('');
    });
    it('sozialUrl nimmt Links oder Profilnamen', () => {
        expect(sozialUrl('instagram', '@mustermann_metallbau')).toBe('https://www.instagram.com/mustermann_metallbau');
        expect(sozialUrl('facebook', 'mustermann')).toBe('https://www.facebook.com/mustermann');
        expect(sozialUrl('linkedin', 'max mustermann')).toBe('https://www.linkedin.com/in/max%20mustermann');
        expect(sozialUrl('facebook', 'www.facebook.com/mustermann')).toBe('https://www.facebook.com/mustermann');
        expect(sozialUrl('instagram', 'javascript:alert(1)')).toBe('');
        expect(sozialUrl('instagram', '@')).toBe('');
        expect(sozialUrl('instagram', '')).toBe('');
    });
    it('websiteAnzeige kürzt Schema und Schrägstrich', () => {
        expect(websiteAnzeige('https://www.example.com/')).toBe('www.example.com');
    });
});

describe('Farben', () => {
    it('sichereFarbe normalisiert und fällt sonst zurück', () => {
        expect(sichereFarbe('#ABCDEF')).toBe('#abcdef');
        expect(sichereFarbe('#abc')).toBe('#aabbcc');
        expect(sichereFarbe('red;background:url(x)')).toBe('#500010');
        expect(sichereFarbe(null, '#111111')).toBe('#111111');
    });
    it('istHexFarbe', () => {
        expect(istHexFarbe('#123')).toBe(true);
        expect(istHexFarbe('#12345')).toBe(false);
        expect(istHexFarbe(undefined)).toBe(false);
    });
    it('kontrastFarbe: weiß auf dunkel, dunkel auf hell', () => {
        expect(kontrastFarbe('#500010')).toBe('#ffffff');
        expect(kontrastFarbe('#fde047')).toBe('#1f2937');
        expect(kontrastFarbe('#ffffff')).toBe('#1f2937');
    });
});
