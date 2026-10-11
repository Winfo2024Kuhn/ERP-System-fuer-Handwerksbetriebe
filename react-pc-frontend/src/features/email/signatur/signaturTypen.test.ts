import { describe, expect, it } from 'vitest';
import { leereSignaturDaten, leseVorlageDaten, schriftartCss, SIGNATUR_DATEN_VERSION, vorlagenInfo } from './signaturTypen';
import { mitFirmenDaten } from './signaturVorausfuellen';

describe('leseVorlageDaten', () => {
    it('liest gespeicherte Daten zurück (Rundreise)', () => {
        const daten = { ...leereSignaturDaten(), vorlage: 'kompakt' as const, name: 'Max Mustermann', schriftart: 'Georgia' as const, social: { facebook: 'mustermann', instagram: '', linkedin: '', whatsapp: '' } };
        expect(leseVorlageDaten(JSON.stringify(daten))).toEqual(daten);
    });

    it('null bei fehlenden oder kaputten Daten', () => {
        expect(leseVorlageDaten(null)).toBeNull();
        expect(leseVorlageDaten('')).toBeNull();
        expect(leseVorlageDaten('{kaputt')).toBeNull();
        expect(leseVorlageDaten('[1,2]')).toBeNull();
        expect(leseVorlageDaten('"text"')).toBeNull();
    });

    it('unbekannte Werte fallen auf Grundwerte zurück', () => {
        const gelesen = leseVorlageDaten(JSON.stringify({ vorlage: 'gibts-nicht', schriftart: 'Comic Sans', name: 42, social: ['x'], fremd: 'egal' }));
        expect(gelesen).toEqual(leereSignaturDaten());
        expect(gelesen?.version).toBe(SIGNATUR_DATEN_VERSION);
    });

    it('teilweise Social-Daten werden ergänzt', () => {
        const gelesen = leseVorlageDaten(JSON.stringify({ social: { instagram: '@mustermann', facebook: 5 } }));
        expect(gelesen?.social).toEqual({ facebook: '', instagram: '@mustermann', linkedin: '', whatsapp: '' });
    });
});

describe('Vorlagen- und Schrift-Infos', () => {
    it('vorlagenInfo und schriftartCss mit Rückfall', () => {
        expect(vorlagenInfo('banner').bilder.banner).toBe(true);
        expect(vorlagenInfo('gibts-nicht' as never).id).toBe('klassisch-foto');
        expect(schriftartCss('Georgia')).toContain('serif');
        expect(schriftartCss('Comic Sans' as never)).toBe('Arial, Helvetica, sans-serif');
    });
});

describe('mitFirmenDaten', () => {
    const firma = {
        firmenname: 'Mustermann Metallbau GmbH', strasse: 'Musterstraße 1', plz: '12345', ort: 'Musterstadt',
        telefon: '0931 123456', fax: '0931 123457', email: 'info@example.com', website: 'www.example.com', firmenfarbe: '#123456',
    };

    it('füllt leere Felder und übernimmt die Firmenfarbe', () => {
        const ergebnis = mitFirmenDaten(leereSignaturDaten(), firma);
        expect(ergebnis).toMatchObject({
            firma: 'Mustermann Metallbau GmbH', telefon: '0931 123456', fax: '0931 123457', email: 'info@example.com',
            website: 'www.example.com', adresse: 'Musterstraße 1, 12345 Musterstadt', akzentfarbe: '#123456',
        });
    });

    it('überschreibt keine Eingaben und keine gewählte Farbe', () => {
        const ergebnis = mitFirmenDaten({ ...leereSignaturDaten(), telefon: '0171 1234567', akzentfarbe: '#0f766e' }, firma);
        expect(ergebnis.telefon).toBe('0171 1234567');
        expect(ergebnis.akzentfarbe).toBe('#0f766e');
    });

    it('lässt Adresse leer, wenn nichts bekannt ist; ungültige Farbe wird ignoriert', () => {
        const ergebnis = mitFirmenDaten(leereSignaturDaten(), { ...firma, strasse: null, plz: null, ort: null, firmenfarbe: 'rot' });
        expect(ergebnis.adresse).toBe('');
        expect(ergebnis.akzentfarbe).toBe('#500010');
    });
});
