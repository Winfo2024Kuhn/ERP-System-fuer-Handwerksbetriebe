/**
 * Abrechnungsstand im PDF-Request. DSGVO: nur Dummy-Daten.
 */
import { describe, it, expect } from 'vitest';
import type { AbrechnungsverlaufDto } from '../../../types';
import {
    baueAbrechnungsverlaufRequest,
    brauchtAbrechnungsverlauf,
    type AbrechnungsverlaufRequestEingabe,
} from './abrechnungsverlaufRequest';

const VERLAUF: AbrechnungsverlaufDto & { basisdokumentDatum?: string } = {
    basisdokumentId: 10,
    basisdokumentNummer: 'AN-2026-0010',
    basisdokumentTyp: 'ANGEBOT',
    basisdokumentDatum: '2026-09-01',
    basisdokumentBetragNetto: 5000,
    positionen: [
        { id: 1, dokumentNummer: 'RE-1', typ: 'RECHNUNG', datum: '2026-09-20', betragNetto: 6000, storniert: false },
    ],
    bereitsAbgerechnet: 6000,
    restbetrag: 0,
};

function eingabe(extra: Partial<AbrechnungsverlaufRequestEingabe> = {}): AbrechnungsverlaufRequestEingabe {
    return {
        verlauf: VERLAUF,
        dokumentId: 1,
        dokumentTyp: 'RECHNUNG',
        isLocked: false,
        gespeichertSchliesstAuftragAb: false,
        balkenAnzeigen: true,
        differenzHinweis: 'Glasfüllung',
        abschlussZeilen: { differenz: { bezeichnung: 'Mehrleistung' } },
        positionenNetto: 6000,
        abschlagBetragNetto: null,
        ...extra,
    };
}

describe('brauchtAbrechnungsverlauf', () => {
    it('gilt nur fuer Rechnungstypen mit Vorgaenger', () => {
        expect(brauchtAbrechnungsverlauf('RECHNUNG', 10)).toBe(true);
        expect(brauchtAbrechnungsverlauf('SCHLUSSRECHNUNG', 10)).toBe(true);
        expect(brauchtAbrechnungsverlauf('ANGEBOT', 10)).toBe(false);
        expect(brauchtAbrechnungsverlauf('RECHNUNG', null)).toBe(false);
    });
});

describe('baueAbrechnungsverlaufRequest', () => {
    it('schickt nichts ohne Auftragssumme', () => {
        expect(baueAbrechnungsverlaufRequest(eingabe({ verlauf: { ...VERLAUF, basisdokumentBetragNetto: 0 } }))).toBeNull();
    });

    it('baut fuer die abschliessende einfache Rechnung den kompletten Stand', () => {
        expect(baueAbrechnungsverlaufRequest(eingabe())).toEqual({
            basisdokumentNummer: 'AN-2026-0010',
            basisdokumentTyp: 'ANGEBOT',
            basisdokumentDatum: '2026-09-01',
            basisdokumentBetragNetto: 5000,
            positionen: [],
            balkenAnzeigen: true,
            differenzHinweis: 'Glasfüllung',
            schliesstAuftragAb: true,
            dieseRechnungZeile: null,
            differenzZeile: { bezeichnung: 'Mehrleistung', unterzeile: null, ausblenden: false },
        });
    });

    it('nimmt stornierte und die eigene Rechnung nicht als Vorrechnung', () => {
        const verlauf = {
            ...VERLAUF,
            positionen: [
                ...VERLAUF.positionen,
                { id: 2, dokumentNummer: 'AR-2', typ: 'ABSCHLAGSRECHNUNG' as const, datum: '2026-09-02', betragNetto: 1000, abschlagsNummer: 1, storniert: false },
                { id: 3, dokumentNummer: 'AR-3', typ: 'ABSCHLAGSRECHNUNG' as const, datum: '2026-09-03', betragNetto: 500, storniert: true },
            ],
        };
        const request = baueAbrechnungsverlaufRequest(eingabe({ verlauf, dokumentTyp: 'SCHLUSSRECHNUNG' }))!;

        expect(request.positionen).toEqual([
            { dokumentNummer: 'AR-2', typ: 'ABSCHLAGSRECHNUNG', datum: '2026-09-02', betragNetto: 1000, abschlagsNummer: 1 },
        ]);
        expect(request.schliesstAuftragAb).toBe(false);
    });

    it('schliesst nicht ab und schickt keine Anpassungen, wenn eine andere Rechnung existiert', () => {
        const verlauf = {
            ...VERLAUF,
            positionen: [...VERLAUF.positionen, { id: 2, dokumentNummer: 'RE-2', typ: 'RECHNUNG' as const, datum: '2026-09-21', betragNetto: 100, storniert: false }],
        };
        const request = baueAbrechnungsverlaufRequest(eingabe({ verlauf }))!;

        expect(request.schliesstAuftragAb).toBe(false);
        expect(request.positionen).toHaveLength(1);
        expect(request.differenzZeile).toBeNull();
    });

    it('schickt gesperrt und abschliessend keine spaeter angelegten Rechnungen mit', () => {
        const verlauf = {
            ...VERLAUF,
            positionen: [...VERLAUF.positionen, { id: 2, dokumentNummer: 'RE-2', typ: 'RECHNUNG' as const, datum: '2026-09-21', betragNetto: 100, storniert: false }],
        };
        const request = baueAbrechnungsverlaufRequest(eingabe({ verlauf, isLocked: true, gespeichertSchliesstAuftragAb: true }))!;

        expect(request.schliesstAuftragAb).toBe(true);
        expect(request.positionen).toEqual([]);
    });

    it('schickt einen leeren Differenzhinweis als null', () => {
        expect(baueAbrechnungsverlaufRequest(eingabe({ differenzHinweis: '' }))!.differenzHinweis).toBeNull();
    });
});
