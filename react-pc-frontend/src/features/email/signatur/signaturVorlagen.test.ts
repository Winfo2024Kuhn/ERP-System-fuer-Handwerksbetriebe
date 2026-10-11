import { describe, expect, it } from 'vitest';
import { hatInhalt, renderSignatur } from './signaturVorlagen';
import { leereSignaturDaten, VORLAGEN, type SignaturDaten, type VorlagenId } from './signaturTypen';

/** Dummy-Daten (DSGVO): Max Mustermann, Mustermann Metallbau GmbH. */
const PIXEL_PNG = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';

function max(teil: Partial<SignaturDaten> = {}): SignaturDaten {
    return {
        ...leereSignaturDaten(),
        name: 'Max Mustermann',
        position: 'Metallbaumeister',
        abteilung: 'Technische Planung',
        firma: 'Mustermann Metallbau GmbH',
        telefon: '0931 123456',
        mobil: '0171 1234567',
        email: 'max@example.com',
        website: 'www.mustermann-metallbau.example',
        adresse: 'Musterstraße 1, 12345 Musterstadt',
        fotoUrl: 'https://example.com/foto.jpg',
        logoUrl: PIXEL_PNG,
        bannerUrl: 'https://example.com/banner.jpg',
        bannerLink: 'https://example.com/aktion',
        social: { facebook: 'mustermann', instagram: '@mustermann', linkedin: '', whatsapp: '0171 1234567' },
        akzentfarbe: '#500010',
        ...teil,
    };
}

function dom(html: string): Document {
    return new DOMParser().parseFromString(`<body>${html}</body>`, 'text/html');
}

const alleVorlagen = VORLAGEN.map((v) => v.id);

describe('renderSignatur – alle Vorlagen', () => {
    it.each(alleVorlagen)('%s: E-Mail-taugliches Tabellen-HTML mit den Angaben', (vorlage) => {
        const html = renderSignatur(max({ vorlage }));
        const doc = dom(html);
        expect(html.startsWith('<table')).toBe(true);
        expect(html).not.toContain('class=');
        expect(html).not.toMatch(/<svg|<div|<script/i);
        for (const tabelle of Array.from(doc.querySelectorAll('table'))) {
            expect(tabelle.getAttribute('cellpadding')).toBe('0');
            expect(tabelle.getAttribute('cellspacing')).toBe('0');
            expect(tabelle.getAttribute('border')).toBe('0');
        }
        expect(doc.body.textContent).toContain('Max Mustermann');
        expect(doc.body.textContent).toContain('Mustermann Metallbau GmbH');
        expect(doc.querySelector('a[href="tel:0931123456"]')).not.toBeNull();
        expect(doc.querySelector('a[href="mailto:max@example.com"]')).not.toBeNull();
        expect(doc.querySelector('a[href="https://www.mustermann-metallbau.example/"]')).not.toBeNull();
        expect(doc.querySelector('a[href="https://wa.me/491711234567"]')).not.toBeNull();
        expect(html).toContain('#500010');
        // Alle Bilder mit fester Breite
        for (const img of Array.from(doc.querySelectorAll('img'))) {
            expect(Number(img.getAttribute('width'))).toBeGreaterThan(0);
            expect(img.getAttribute('alt')).not.toBeNull();
        }
    });

    it('Klassisch mit Foto: Foto und Logo links, Kontakt-Kästchen in der Akzentfarbe', () => {
        const doc = dom(renderSignatur(max({ vorlage: 'klassisch-foto' })));
        const bilder = Array.from(doc.querySelectorAll('img')).map((i) => i.getAttribute('src'));
        expect(bilder).toEqual(['https://example.com/foto.jpg', PIXEL_PNG]);
        expect(doc.querySelector('img')?.getAttribute('width')).toBe('130');
        const kaestchen = Array.from(doc.querySelectorAll('td')).filter((td) => td.getAttribute('style')?.includes('background-color:#500010') && td.textContent?.length === 1);
        expect(kaestchen.map((k) => k.textContent)).toEqual(['T', 'M', '@', 'W', 'A']);
    });

    it('Mit Banner: Banner verlinkt, Foto erscheint nicht', () => {
        const doc = dom(renderSignatur(max({ vorlage: 'banner' })));
        const banner = doc.querySelector('img[src="https://example.com/banner.jpg"]');
        expect(banner?.closest('a')?.getAttribute('href')).toBe('https://example.com/aktion');
        expect(doc.querySelector('img[src="https://example.com/foto.jpg"]')).toBeNull();
    });

    it.each<[VorlagenId, boolean]>([['klassisch-foto', true], ['modern-balken', true], ['kompakt', false], ['banner', false], ['schlicht', false]])(
        '%s zeigt das Foto: %s', (vorlage, mitFoto) => {
            const doc = dom(renderSignatur(max({ vorlage })));
            expect(!!doc.querySelector('img[src="https://example.com/foto.jpg"]')).toBe(mitFoto);
        },
    );

    it('Schriftart und Zusatztext (mehrzeilig) landen im HTML', () => {
        const html = renderSignatur(max({ vorlage: 'schlicht', schriftart: 'Trebuchet MS', zusatz: 'Amtsgericht Musterstadt HRB 12345\nGeschäftsführer: Max Mustermann' }));
        expect(html).toContain("font-family:'Trebuchet MS', Helvetica, sans-serif;");
        expect(html).toContain('Amtsgericht Musterstadt HRB 12345<br>Geschäftsführer: Max Mustermann');
    });

    it('helle Akzentfarbe bekommt dunkle Schrift in den Kästchen', () => {
        const html = renderSignatur(max({ akzentfarbe: '#fde047' }));
        expect(html).toContain('color:#1f2937');
    });

    it('ungültige Akzentfarbe fällt auf die Standardfarbe zurück (kein CSS-Einschleusen)', () => {
        const html = renderSignatur(max({ akzentfarbe: 'red;background:url(https://boese.example)' }));
        expect(html).not.toContain('boese.example');
        expect(html).toContain('#500010');
    });
});

describe('renderSignatur – leere Felder', () => {
    it.each(alleVorlagen)('%s: leere Felder fallen ersatzlos weg', (vorlage) => {
        const html = renderSignatur({ ...leereSignaturDaten(), vorlage, name: 'Max Mustermann' });
        const doc = dom(html);
        expect(doc.body.textContent?.replace(/\s+/g, ' ').trim()).toBe('Max Mustermann');
        expect(doc.querySelectorAll('img')).toHaveLength(0);
        expect(doc.querySelectorAll('a')).toHaveLength(0);
        expect(html).not.toContain('|');
        expect(html).not.toContain('·');
    });

    it.each(alleVorlagen)('%s: ganz leer ergibt keinen sichtbaren Text', (vorlage) => {
        const doc = dom(renderSignatur({ ...leereSignaturDaten(), vorlage }));
        expect(doc.body.textContent?.trim()).toBe('');
    });
});

describe('renderSignatur – Sicherheit', () => {
    const boese = max({
        name: '<script>alert(1)</script>',
        position: '"><img src=x onerror=alert(1)>',
        firma: "Max' & \"Söhne\"",
        zusatz: '<b>fett</b>',
        website: 'javascript:alert(1)',
        email: 'x"onmouseover="alert(1)@example.com',
        telefon: '"><script>',
        fotoUrl: 'javascript:alert(1)',
        logoUrl: 'data:image/svg+xml;base64,PHN2ZyBvbmxvYWQ9YWxlcnQoMSk+',
        bannerUrl: 'data:text/html;base64,PHNjcmlwdD4=',
        bannerLink: 'javascript:alert(1)',
        social: { facebook: 'javascript:alert(1)', instagram: 'data:text/html,x', linkedin: '"><script>', whatsapp: 'javascript:alert(1)' },
    });

    it.each(alleVorlagen)('%s: Eingaben sind escaped, gefährliche Links und Bilder verworfen', (vorlage) => {
        const html = renderSignatur({ ...boese, vorlage });
        const doc = dom(html);
        expect(doc.querySelector('script')).toBeNull();
        expect(html).not.toMatch(/<script/i);
        expect(html).not.toMatch(/<img src=x/i);
        expect(html).toContain('&lt;script&gt;alert(1)&lt;/script&gt;');
        expect(html).toContain('&quot;&gt;&lt;img src=x onerror=alert(1)&gt;');
        expect(html).toContain('Max&#39; &amp; &quot;Söhne&quot;');
        for (const el of Array.from(doc.querySelectorAll('*'))) {
            for (const attr of Array.from(el.attributes)) {
                expect(attr.name.startsWith('on')).toBe(false);
                expect(attr.value.toLowerCase()).not.toContain('javascript:');
                expect(attr.value.toLowerCase()).not.toMatch(/^data:(?!image\/(png|jpeg|gif|webp);base64,)/);
            }
        }
        expect(doc.querySelectorAll('img')).toHaveLength(0);
        // Website mit javascript: erscheint gar nicht
        expect(doc.body.textContent).not.toContain('javascript:');
        // LinkedIn-Eingabe mit Sonderzeichen wird zum harmlosen, kodierten Profil-Link
        const linkedin = doc.querySelector('a[href^="https://www.linkedin.com/in/"]');
        if (linkedin) expect(linkedin.getAttribute('href')).toContain('%22%3E%3Cscript%3E');
    });

    it('erlaubte Data-URIs bleiben, unerlaubte Typen fallen weg', () => {
        const mitPng = dom(renderSignatur(max({ vorlage: 'klassisch-foto', fotoUrl: PIXEL_PNG, logoUrl: 'data:image/gif;base64,R0lGODlhAQABAAAAACw=' })));
        expect(mitPng.querySelectorAll('img')).toHaveLength(2);
        const mitSvg = dom(renderSignatur(max({ vorlage: 'klassisch-foto', fotoUrl: 'data:image/svg+xml;base64,PHN2Zz4=', logoUrl: '' })));
        expect(mitSvg.querySelectorAll('img')).toHaveLength(0);
    });
});

describe('hatInhalt', () => {
    it('erkennt leere und gefüllte Signaturen', () => {
        expect(hatInhalt(leereSignaturDaten())).toBe(false);
        expect(hatInhalt({ ...leereSignaturDaten(), name: '  ' })).toBe(false);
        expect(hatInhalt({ ...leereSignaturDaten(), firma: 'Mustermann Metallbau GmbH' })).toBe(true);
        expect(hatInhalt({ ...leereSignaturDaten(), logoUrl: PIXEL_PNG })).toBe(true);
        expect(hatInhalt({ ...leereSignaturDaten(), logoUrl: 'javascript:alert(1)' })).toBe(false);
    });
});
