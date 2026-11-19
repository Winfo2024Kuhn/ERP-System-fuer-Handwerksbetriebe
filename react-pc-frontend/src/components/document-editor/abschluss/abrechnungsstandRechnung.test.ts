/**
 * Rechenweg des Abrechnungsstands im Abschluss. Muss dieselben Zahlen liefern
 * wie RechnungPdfService, sonst zeigen Editor und PDF Verschiedenes.
 */
import { describe, it, expect } from 'vitest';
import { abrechnungsstandSichtbar, abschlagUnterzeile, berechneAbrechnungsstand, mitSteuer } from './abrechnungsstandRechnung';

const basis = {
    positionenNetto: 0,
    gesamtNettoEffektiv: 0,
    abschlagBetragNetto: null,
    bereitsAbgerechnetDurchAndere: null,
    basisdokumentBetragNetto: null,
    schliesstAuftragAb: false,
};

describe('berechneAbrechnungsstand – einfache Rechnung', () => {
    it('zeigt den Abrechnungsstand, wenn sie den Auftrag abschliesst', () => {
        // Auftrag 5.404,00 netto, abgerechnet 6.479,74 netto -> 1.075,75 zusaetzlich.
        const stand = berechneAbrechnungsstand({
            ...basis,
            dokumentTyp: 'RECHNUNG',
            positionenNetto: 6479.75,
            gesamtNettoEffektiv: 6479.75,
            basisdokumentBetragNetto: 5404,
            schliesstAuftragAb: true,
        });

        expect(stand.anzeigen).toBe(true);
        expect(stand.dieseLabel).toBe('diese Rechnung');
        expect(stand.auftragBrutto).toBeCloseTo(6430.76, 2);
        expect(stand.dieseNetto).toBeCloseTo(6479.75, 2);
        expect(stand.zeigeDifferenz).toBe(true);
        expect(stand.differenzEntfallen).toBe(false);
        expect(stand.differenzBrutto).toBeCloseTo(1280.14, 2);
        expect(stand.prozent).toBe(100);
        expect(stand.restNetto).toBeCloseTo(0, 2);
    });

    it('zeigt nichts, wenn sie den Auftrag nicht abschliesst', () => {
        const stand = berechneAbrechnungsstand({
            ...basis, dokumentTyp: 'RECHNUNG', positionenNetto: 100, gesamtNettoEffektiv: 100, basisdokumentBetragNetto: 500,
        });
        expect(stand.anzeigen).toBe(false);
    });

    it('zeigt nichts ohne Auftragssumme (wie das PDF)', () => {
        const stand = berechneAbrechnungsstand({
            ...basis, dokumentTyp: 'RECHNUNG', positionenNetto: 100, gesamtNettoEffektiv: 100, schliesstAuftragAb: true,
        });
        expect(stand.anzeigen).toBe(false);
    });
});

describe('berechneAbrechnungsstand – Bezeichnungen wie im PDF', () => {
    it('benennt die Zeile genau wie RechnungAbrechnungsstandPdf', () => {
        const fuer = (dokumentTyp: string, extra = {}) => berechneAbrechnungsstand({
            ...basis, dokumentTyp, positionenNetto: 100, gesamtNettoEffektiv: 100, ...extra,
        }).dieseLabel;
        expect(fuer('ABSCHLAGSRECHNUNG', { abschlagBetragNetto: 50 })).toBe('diese Abschlagsrechnung');
        expect(fuer('TEILRECHNUNG')).toBe('diese Rechnung');
        expect(fuer('SCHLUSSRECHNUNG')).toBe('diese Schlussrechnung');
        expect(fuer('RECHNUNG', { schliesstAuftragAb: true, basisdokumentBetragNetto: 100 })).toBe('diese Rechnung');
    });
});

describe('berechneAbrechnungsstand – Zeile „Noch offen“ wie im PDF', () => {
    const fuer = (extra: Record<string, unknown>) => berechneAbrechnungsstand({
        ...basis, positionenNetto: 100, gesamtNettoEffektiv: 100, basisdokumentBetragNetto: 100, ...extra,
    }).zeigeRest;

    it('entfaellt bei der einfachen Rechnung, die den ganzen Auftrag ohne Vorrechnung abrechnet', () => {
        expect(fuer({ dokumentTyp: 'RECHNUNG', schliesstAuftragAb: true })).toBe(false);
    });

    it('bleibt bei Abschlag und Schlussrechnung immer stehen', () => {
        expect(fuer({ dokumentTyp: 'ABSCHLAGSRECHNUNG', abschlagBetragNetto: 100 })).toBe(true);
        expect(fuer({ dokumentTyp: 'SCHLUSSRECHNUNG' })).toBe(true);
    });

    it('bleibt bei Vorrechnungen oder offenem Rest stehen', () => {
        expect(fuer({ dokumentTyp: 'TEILRECHNUNG', bereitsAbgerechnetDurchAndere: 0 })).toBe(false);
        expect(fuer({ dokumentTyp: 'TEILRECHNUNG', basisdokumentBetragNetto: 500 })).toBe(true);
        expect(fuer({ dokumentTyp: 'TEILRECHNUNG', basisdokumentBetragNetto: 200, bereitsAbgerechnetDurchAndere: 100 })).toBe(true);
    });
});

describe('abschlagUnterzeile (wie im PDF)', () => {
    const stand = (dieseNetto: number, auftragNetto: number) => ({ istAbschlag: true, dieseNetto, auftragNetto });

    it('nennt den eingegebenen Prozentsatz ohne unnoetige Nullen', () => {
        expect(abschlagUnterzeile(stand(300, 1000), { modus: 'prozent', eingabeWert: 30 })).toBe('ca. 30 % der Auftragssumme');
        expect(abschlagUnterzeile(stand(125, 1000), { modus: 'prozent', eingabeWert: 12.5 })).toBe('ca. 12,5 % der Auftragssumme');
    });

    it('nennt den eingegebenen Bruttobetrag', () => {
        expect(abschlagUnterzeile(stand(1000, 5000), { modus: 'brutto', eingabeWert: 1190 })).toBe('Eingabe: 1.190,00 € brutto');
    });

    it('rechnet sonst den Anteil auf eine Nachkommastelle', () => {
        expect(abschlagUnterzeile(stand(1000, 3000), null)).toBe('ca. 33,3 % der Auftragssumme');
        expect(abschlagUnterzeile(stand(1500, 3000), { modus: 'netto', eingabeWert: 1500 })).toBe('ca. 50 % der Auftragssumme');
    });

    it('hat ohne Abschlag oder Auftragssumme keine Unterzeile', () => {
        expect(abschlagUnterzeile({ istAbschlag: false, dieseNetto: 1, auftragNetto: 2 }, null)).toBeNull();
        expect(abschlagUnterzeile(stand(100, 0), null)).toBeNull();
    });
});

describe('abrechnungsstandSichtbar', () => {
    it('folgt derselben Regel wie die Anzeige im Abschluss', () => {
        expect(abrechnungsstandSichtbar({ dokumentTyp: 'RECHNUNG', positionenNetto: 100, basisdokumentBetragNetto: 100, schliesstAuftragAb: true })).toBe(true);
        expect(abrechnungsstandSichtbar({ dokumentTyp: 'RECHNUNG', positionenNetto: 100, basisdokumentBetragNetto: 100, schliesstAuftragAb: false })).toBe(false);
        expect(abrechnungsstandSichtbar({ dokumentTyp: 'TEILRECHNUNG', positionenNetto: 0 })).toBe(false);
    });
});

describe('mitSteuer', () => {
    it('rundet halbe Cent wie BigDecimal HALF_UP', () => {
        expect(mitSteuer(42.5)).toBeCloseTo(50.58, 2);
        expect(mitSteuer(-42.5)).toBeCloseTo(-50.58, 2);
    });
});
