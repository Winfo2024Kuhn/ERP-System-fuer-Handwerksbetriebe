import { describe, expect, it } from 'vitest';
import {
    aufFirmenVorlageUmstellen,
    firmenVorlagenDaten,
    GESPERRTE_FELDER,
    istGesperrtesFeld,
    mitFirmenVorlage,
    neuAusFirmenVorlage,
    wechsleFirmenVorlage,
} from './firmenVorlage';
import { leereSignaturDaten, type SignaturDaten } from './signaturTypen';

/** Firmen-Vorlage: welche Felder der Betrieb festlegt und welche jeder selbst einträgt. Nur Dummy-Daten. */

const VORLAGE: SignaturDaten = {
    ...leereSignaturDaten(),
    vorlage: 'banner',
    akzentfarbe: '#1e3a8a',
    schriftart: 'Georgia',
    logoUrl: 'https://example.com/logo.png',
    bannerUrl: 'https://example.com/banner.png',
    bannerLink: 'https://example.com/aktion',
    firma: 'Mustermann Metallbau GmbH',
    fax: '0931 123457',
    website: 'www.example.com',
    adresse: 'Musterstraße 1, 12345 Musterstadt',
    slogan: 'Metallbau seit 1980',
    zusatz: 'Amtsgericht Musterstadt HRB 12345',
    social: { facebook: 'mustermann', instagram: '@mustermann', linkedin: '', whatsapp: '' },
    // Persönliche Angaben der Vorlage dürfen nicht übernommen werden – außer der Telefonnummer beim Start.
    name: 'Erika Musterfrau',
    position: 'Geschäftsführerin',
    abteilung: 'Büro',
    telefon: '0931 123456',
    mobil: '0171 1111111',
    email: 'erika@example.com',
    fotoUrl: 'https://example.com/erika.jpg',
};

const PERSOENLICH: SignaturDaten = {
    ...leereSignaturDaten(),
    name: 'Max Mustermann',
    position: 'Metallbaumeister',
    abteilung: 'Technische Planung',
    telefon: '0931 123456-12',
    mobil: '0171 1234567',
    email: 'max@example.com',
    fotoUrl: 'https://example.com/max.jpg',
    firma: 'Alte Firma',
    slogan: 'Alter Slogan',
};

describe('gesperrte Felder', () => {
    it('kennt genau die Felder, die der Betrieb festlegt', () => {
        expect(GESPERRTE_FELDER).toHaveLength(13);
        expect(istGesperrtesFeld('firma')).toBe(true);
        expect(istGesperrtesFeld('social')).toBe(true);
        for (const frei of ['name', 'position', 'abteilung', 'telefon', 'mobil', 'email', 'fotoUrl'] as const) {
            expect(istGesperrtesFeld(frei)).toBe(false);
        }
    });
});

describe('firmenVorlagenDaten', () => {
    it('liest die Formulardaten der Vorlage, sonst Grundwerte', () => {
        expect(firmenVorlagenDaten({ vorlageDaten: JSON.stringify(VORLAGE) }).firma).toBe('Mustermann Metallbau GmbH');
        expect(firmenVorlagenDaten({ vorlageDaten: null })).toEqual(leereSignaturDaten());
    });
});

describe('mitFirmenVorlage', () => {
    it('übernimmt nur gesperrte Felder, persönliche bleiben', () => {
        const ergebnis = mitFirmenVorlage(PERSOENLICH, VORLAGE);
        for (const feld of GESPERRTE_FELDER) expect(ergebnis[feld]).toEqual(VORLAGE[feld]);
        expect(ergebnis).toMatchObject({
            name: 'Max Mustermann', position: 'Metallbaumeister', abteilung: 'Technische Planung', telefon: '0931 123456-12',
            mobil: '0171 1234567', email: 'max@example.com', fotoUrl: 'https://example.com/max.jpg',
        });
    });

    it('kopiert die sozialen Netzwerke statt sie zu teilen', () => {
        const ergebnis = mitFirmenVorlage(PERSOENLICH, VORLAGE);
        expect(ergebnis.social).not.toBe(VORLAGE.social);
    });
});

describe('neuAusFirmenVorlage', () => {
    it('belegt nur Namen und Telefon vor, keine persönlichen Angaben der Vorlage', () => {
        const ergebnis = neuAusFirmenVorlage(VORLAGE, 'Max Mustermann');
        expect(ergebnis).toMatchObject({ name: 'Max Mustermann', telefon: '0931 123456', position: '', abteilung: '', mobil: '', email: '', fotoUrl: '' });
        expect(ergebnis.firma).toBe('Mustermann Metallbau GmbH');
        expect(ergebnis.vorlage).toBe('banner');
    });
});

describe('aufFirmenVorlageUmstellen', () => {
    it('behält ein eigenes Telefon, füllt ein leeres aus der Vorlage', () => {
        expect(aufFirmenVorlageUmstellen(PERSOENLICH, VORLAGE).telefon).toBe('0931 123456-12');
        expect(aufFirmenVorlageUmstellen({ ...PERSOENLICH, telefon: '  ' }, VORLAGE).telefon).toBe('0931 123456');
    });
});

describe('wechsleFirmenVorlage', () => {
    const neu = { ...VORLAGE, vorlage: 'kompakt' as const, telefon: '0931 999999', firma: 'Mustermann Montage GmbH' };

    it('nimmt die Telefonnummer der neuen Vorlage mit, solange sie unverändert ist', () => {
        const daten = neuAusFirmenVorlage(VORLAGE, 'Max Mustermann');
        const ergebnis = wechsleFirmenVorlage(daten, VORLAGE, neu);
        expect(ergebnis).toMatchObject({ telefon: '0931 999999', firma: 'Mustermann Montage GmbH', vorlage: 'kompakt', name: 'Max Mustermann' });
        expect(wechsleFirmenVorlage({ ...daten, telefon: '' }, VORLAGE, neu).telefon).toBe('0931 999999');
    });

    it('eine selbst eingetragene Durchwahl bleibt', () => {
        const ergebnis = wechsleFirmenVorlage(PERSOENLICH, VORLAGE, neu);
        expect(ergebnis.telefon).toBe('0931 123456-12');
    });
});
