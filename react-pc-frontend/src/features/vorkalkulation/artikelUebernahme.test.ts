import { describe, expect, it } from 'vitest';
import { alsEntwurf, stammEinheit, zeileAusArtikel } from './artikelUebernahme';
import { positionsMengen } from './berechnung';
import type { Artikel } from '../../types';

/**
 * Die Uebernahme aus dem Materialstamm ist die fehleranfaelligste Stelle der
 * Kalkulation: Ein falscher Preisbezug multipliziert den Materialpreis
 * stillschweigend mit dem Gewicht und zieht sich bis in den Verkaufspreis
 * durch. Deshalb liegt hier ein eigener Testblock.
 */

const artikel = (werte: Partial<Artikel>): Artikel => ({
    id: 1,
    produktname: 'Dummy-Profil',
    ...werte,
} as Artikel);

describe('stammEinheit', () => {
    it('uebersetzt die Verrechnungseinheit in die Mengeneinheit der Kalkulation', () => {
        expect(stammEinheit(artikel({ verrechnungseinheit: 'LAUFENDE_METER' }))).toBe('METER');
        expect(stammEinheit(artikel({ verrechnungseinheit: 'QUADRATMETER' }))).toBe('QUADRATMETER');
        expect(stammEinheit(artikel({ verrechnungseinheit: 'KILOGRAMM' }))).toBe('KILOGRAMM');
        expect(stammEinheit(artikel({ verrechnungseinheit: 'STUECK' }))).toBe('STUECK');
    });

    it('versteht die Objektform, die manche Endpunkte liefern', () => {
        expect(stammEinheit(artikel({
            verrechnungseinheit: { name: 'LAUFENDE_METER', anzeigename: 'Laufende Meter' },
        }))).toBe('METER');
    });

    it('faellt ohne Angabe auf Stueck zurueck', () => {
        expect(stammEinheit(artikel({}))).toBe('STUECK');
    });
});

describe('zeileAusArtikel — Preisbezug', () => {
    /**
     * Regression: Der Stammpreis ist der Preis je Verrechnungseinheit
     * (`ArtikelPositionsPreisService.umrechnungsfaktor` liefert fuer alles
     * ausser KILOGRAMM den Faktor 1). Wurde er faelschlich als Kilopreis
     * uebernommen, kostete 1 Meter Flachstahl das 7,85-Fache.
     */
    it('nimmt den Stammpreis je Einheit, nicht je Kilogramm', () => {
        const zeile = zeileAusArtikel(artikel({
            verrechnungseinheit: 'LAUFENDE_METER',
            guenstigsterPreis: 9.42,
            kgProMeter: 7.85,
        }), '10', 'LAGER');

        expect(zeile.preisbezug).toBe('EINHEIT');
        // 10 m × 9,42 EUR/m = 94,20 EUR — nicht 78,5 kg × 9,42 EUR.
        expect(positionsMengen(zeile).kosten).toBe(94.2);
        // Das Gewicht wird trotzdem gefuehrt, es wird nur nicht bepreist.
        expect(positionsMengen(zeile).kilogramm).toBeCloseTo(78.5, 4);
    });

    it('bleibt auch bei Kilogramm-Ware bei "je Einheit", weil die Menge dort kg ist', () => {
        const zeile = zeileAusArtikel(artikel({
            verrechnungseinheit: 'KILOGRAMM',
            guenstigsterPreis: 1.48,
        }), '250', 'LAGER');

        expect(zeile.einheit).toBe('KILOGRAMM');
        expect(zeile.preisbezug).toBe('EINHEIT');
        expect(positionsMengen(zeile).kosten).toBe(370);
        expect(positionsMengen(zeile).kilogramm).toBe(250);
    });
});

describe('zeileAusArtikel — uebernommene Stammdaten', () => {
    it('holt Gewicht und Mantelflaeche fuer Verzinkung und Beschichtung mit', () => {
        const zeile = zeileAusArtikel(artikel({
            verrechnungseinheit: 'LAUFENDE_METER',
            kgProMeter: 4.25,
            mantelflaeche: 0.2,
        }), '18,3888', 'LAGER');

        expect(zeile.kgJeEinheit).toBe('4,25');
        expect(zeile.qmJeEinheit).toBe('0,2');
        expect(positionsMengen(zeile).kilogramm).toBeCloseTo(78.1524, 4);
    });

    it('nimmt bei Blech das Gewicht je Quadratmeter', () => {
        const zeile = zeileAusArtikel(artikel({
            verrechnungseinheit: 'QUADRATMETER',
            kgProQm: 7.85,
            kgProMeter: undefined,
        }), '3', 'LAGER');

        expect(zeile.einheit).toBe('QUADRATMETER');
        expect(zeile.kgJeEinheit).toBe('7,85');
        expect(positionsMengen(zeile).kilogramm).toBeCloseTo(23.55, 4);
    });

    it('schlaegt Verzinken vor, wenn der Stamm den Artikel dafuer vorsieht', () => {
        const geeignet = zeileAusArtikel(artikel({ verzinkungsgeeignet: true }), '1', 'LAGER');
        expect(geeignet.verzinkbar).toBe(true);
        expect(geeignet.verzinken).toBe(true);

        const ungeeignet = zeileAusArtikel(artikel({ verzinkungsgeeignet: false }), '1', 'LAGER');
        expect(ungeeignet.verzinkbar).toBe(false);
        expect(ungeeignet.verzinken).toBe(false);
    });

    it('schlaegt Pulverbeschichten nie von selbst vor — das ist eine Extra-Entscheidung', () => {
        const zeile = zeileAusArtikel(artikel({ pulverbeschichtungsgeeignet: true }), '1', 'LAGER');
        expect(zeile.pulverbeschichtbar).toBe(true);
        expect(zeile.pulverbeschichten).toBe(false);
    });

    it('uebernimmt die Beschaffung aus dem Lager-Umschalter', () => {
        expect(zeileAusArtikel(artikel({}), '1', 'BESTELLEN').beschaffung).toBe('BESTELLEN');
        expect(zeileAusArtikel(artikel({}), '1', 'LAGER').beschaffung).toBe('LAGER');
    });

    it('laesst den Preis leer, wenn kein Lieferantenpreis gepflegt ist', () => {
        expect(zeileAusArtikel(artikel({ guenstigsterPreis: undefined, preis: undefined }), '1', 'LAGER').preis).toBe('');
    });
});

describe('alsEntwurf', () => {
    it('schreibt deutsches Komma ohne Tausenderpunkte', () => {
        expect(alsEntwurf(1234.5)).toBe('1234,5');
        expect(alsEntwurf(7.85)).toBe('7,85');
    });

    it('macht aus fehlenden Werten einen leeren Entwurf, keine erfundene Null', () => {
        expect(alsEntwurf(undefined)).toBe('');
        expect(alsEntwurf(null)).toBe('');
    });
});
