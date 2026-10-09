import { describe, it, expect } from 'vitest';
import {
    ALTER_GRENZE_TAGE,
    TYP_LABELS,
    formatiereAlter,
    fortschrittsStufe,
    istAlt,
    kettenBetrag,
    letzteBewegung,
    parseIsoDatum,
    passtZurSuche,
    teileNachAlter,
    type Kette,
    type KettenDokument,
} from './bestellungenListe';

const HEUTE = new Date(2026, 9, 5);

function dok(teil: Partial<KettenDokument>): KettenDokument {
    return {
        typ: 'ANGEBOT', dokumentNummer: null, dokumentDatum: null, betragBrutto: null, dateiname: 'datei.pdf', ...teil,
    };
}
function kette(dokumente: KettenDokument[], lieferantName: string | null = 'Max Mustermann GmbH'): Kette {
    return { lieferantName, dokumente };
}

describe('parseIsoDatum', () => {
    it('liest Datum und Datum mit Uhrzeit lokal', () => {
        expect(parseIsoDatum('2026-03-04')).toEqual(new Date(2026, 2, 4));
        expect(parseIsoDatum('2026-03-04T23:59:00')).toEqual(new Date(2026, 2, 4));
    });
    it('liefert null bei leer oder ungültig', () => {
        expect(parseIsoDatum(null)).toBeNull();
        expect(parseIsoDatum(undefined)).toBeNull();
        expect(parseIsoDatum('')).toBeNull();
        expect(parseIsoDatum('04.03.2026')).toBeNull();
    });
});

describe('letzteBewegung', () => {
    it('nimmt das jüngste Dokument und fällt auf das Eingangsdatum zurück', () => {
        const k = kette([
            dok({ dokumentDatum: '2026-01-10' }),
            dok({ dokumentDatum: null, eingangsDatum: '2026-02-01' }),
            dok({ dokumentDatum: '2026-01-20' }),
        ]);
        expect(letzteBewegung(k)).toEqual(new Date(2026, 1, 1));
    });
    it('ist null ohne jedes Datum', () => {
        expect(letzteBewegung(kette([dok({})]))).toBeNull();
        expect(letzteBewegung(kette([]))).toBeNull();
    });
});

describe('istAlt / teileNachAlter', () => {
    const frisch = kette([dok({ dokumentDatum: '2026-09-20' })]);
    const alt = kette([dok({ dokumentDatum: '2026-05-01' })]);
    const ohneDatum = kette([dok({})]);
    const genauAnDerGrenze = kette([dok({ dokumentDatum: '2026-08-06' })]); // 60 Tage
    const einenTagDarueber = kette([dok({ dokumentDatum: '2026-08-05' })]); // 61 Tage

    it('erkennt alte Ketten', () => {
        expect(ALTER_GRENZE_TAGE).toBe(60);
        expect(istAlt(frisch, HEUTE)).toBe(false);
        expect(istAlt(alt, HEUTE)).toBe(true);
        expect(istAlt(genauAnDerGrenze, HEUTE)).toBe(false);
        expect(istAlt(einenTagDarueber, HEUTE)).toBe(true);
    });
    it('lässt Ketten ohne Datum sichtbar', () => {
        expect(istAlt(ohneDatum, HEUTE)).toBe(false);
    });
    it('respektiert eine eigene Grenze', () => {
        expect(istAlt(frisch, HEUTE, 10)).toBe(true);
    });
    it('teilt in aktuell und älter', () => {
        const teile = teileNachAlter([frisch, alt, ohneDatum], HEUTE);
        expect(teile.aktuell).toEqual([frisch, ohneDatum]);
        expect(teile.aelter).toEqual([alt]);
        expect(teileNachAlter([frisch, alt], HEUTE, 10).aktuell).toEqual([]);
    });
});

describe('formatiereAlter', () => {
    const tage = (n: number) => new Date(2026, 9, 5 - n);
    it('benennt Zeiträume in Handwerker-Sprache', () => {
        expect(formatiereAlter(null, HEUTE)).toBe('Datum unbekannt');
        expect(formatiereAlter(tage(0), HEUTE)).toBe('heute');
        expect(formatiereAlter(tage(-3), HEUTE)).toBe('heute');
        expect(formatiereAlter(tage(1), HEUTE)).toBe('gestern');
        expect(formatiereAlter(tage(5), HEUTE)).toBe('vor 5 Tagen');
        expect(formatiereAlter(tage(21), HEUTE)).toBe('vor 3 Wochen');
        expect(formatiereAlter(tage(90), HEUTE)).toBe('vor 3 Monaten');
        expect(formatiereAlter(tage(400), HEUTE)).toBe('vor über einem Jahr');
        expect(formatiereAlter(tage(800), HEUTE)).toBe('vor 2 Jahren');
    });
});

describe('fortschrittsStufe', () => {
    it('zählt den weitesten Schritt', () => {
        expect(fortschrittsStufe(kette([]))).toBe(0);
        expect(fortschrittsStufe(kette([dok({ typ: 'SONSTIG' })]))).toBe(0);
        // Ein Werkstoffzeugnis ist weder Lieferung noch Rechnung
        expect(fortschrittsStufe(kette([dok({ typ: 'WERKSTOFFZEUGNIS' })]))).toBe(0);
        expect(fortschrittsStufe(kette([dok({ typ: 'LIEFERSCHEIN' }), dok({ typ: 'WERKSTOFFZEUGNIS' })]))).toBe(3);
        expect(fortschrittsStufe(kette([dok({ typ: 'ANGEBOT' })]))).toBe(1);
        expect(fortschrittsStufe(kette([dok({ typ: 'ANGEBOT' }), dok({ typ: 'AUFTRAGSBESTAETIGUNG' })]))).toBe(2);
        expect(fortschrittsStufe(kette([dok({ typ: 'LIEFERSCHEIN' })]))).toBe(3);
        expect(fortschrittsStufe(kette([dok({ typ: 'RECHNUNG' })]))).toBe(4);
        expect(fortschrittsStufe(kette([dok({ typ: 'GUTSCHRIFT' })]))).toBe(4);
    });
});

describe('kettenBetrag', () => {
    it('bevorzugt Rechnung vor AB vor Angebot', () => {
        const k = kette([
            dok({ typ: 'ANGEBOT', betragBrutto: 100 }),
            dok({ typ: 'AUFTRAGSBESTAETIGUNG', betragBrutto: 200 }),
        ]);
        expect(kettenBetrag(k)).toEqual({ betrag: 200, typ: 'AUFTRAGSBESTAETIGUNG', anzahl: 1 });
        k.dokumente.push(dok({ typ: 'RECHNUNG', betragBrutto: 300 }));
        expect(kettenBetrag(k)).toEqual({ betrag: 300, typ: 'RECHNUNG', anzahl: 1 });
    });
    it('zählt Teilrechnungen zusammen', () => {
        const k = kette([
            dok({ typ: 'AUFTRAGSBESTAETIGUNG', betragBrutto: 1600 }),
            dok({ typ: 'RECHNUNG', betragBrutto: 1517.64 }),
            dok({ typ: 'RECHNUNG', betragBrutto: 109.25 }),
            dok({ typ: 'RECHNUNG', betragBrutto: null }),
        ]);
        expect(kettenBetrag(k)).toEqual({ betrag: 1626.89, typ: 'RECHNUNG', anzahl: 2 });
    });
    it('überspringt fehlende und ungültige Beträge', () => {
        const k = kette([
            dok({ typ: 'RECHNUNG', betragBrutto: null }),
            dok({ typ: 'AUFTRAGSBESTAETIGUNG', betragBrutto: Number.NaN }),
            dok({ typ: 'ANGEBOT', betragBrutto: 50 }),
        ]);
        expect(kettenBetrag(k)).toEqual({ betrag: 50, typ: 'ANGEBOT', anzahl: 1 });
        expect(kettenBetrag(kette([dok({ typ: 'SONSTIG', betragBrutto: 5 })]))).toBeNull();
        expect(kettenBetrag(kette([dok({ typ: 'WERKSTOFFZEUGNIS', betragBrutto: 5 })]))).toBeNull();
    });
});

describe('passtZurSuche', () => {
    const k = kette([
        dok({ typ: 'ANGEBOT', dokumentNummer: 'AN-2026-17', dokumentDatum: '2026-03-04', betragBrutto: 1234.5, dateiname: 'Stahltraeger.pdf' }),
        dok({ typ: 'LIEFERSCHEIN', eingangsDatum: '2026-03-10', dateiname: 'ls.pdf' }),
    ]);
    it('findet über Lieferant, Nummer, Dateiname, Typ', () => {
        expect(passtZurSuche(k, 'mustermann')).toBe(true);
        expect(passtZurSuche(k, 'AN-2026')).toBe(true);
        expect(passtZurSuche(k, 'stahltraeger')).toBe(true);
        expect(passtZurSuche(k, 'lieferschein')).toBe(true);
    });
    it('findet über Betrag mit und ohne Tausenderpunkt und mit Euro-Zeichen', () => {
        expect(passtZurSuche(k, '1.234,50')).toBe(true);
        expect(passtZurSuche(k, '1234,50 €')).toBe(true);
        expect(passtZurSuche(k, '999,00')).toBe(false);
    });
    it('findet über Datum, auch das Eingangsdatum', () => {
        expect(passtZurSuche(k, '04.03.2026')).toBe(true);
        expect(passtZurSuche(k, '10.03.2026')).toBe(true);
    });
    it('verlangt alle Wörter, ignoriert Groß-/Kleinschreibung', () => {
        expect(passtZurSuche(k, 'MUSTERMANN angebot')).toBe(true);
        expect(passtZurSuche(k, 'mustermann rechnung')).toBe(false);
    });
    it('leere Suche trifft alles; fehlende Felder stören nicht', () => {
        expect(passtZurSuche(k, '   ')).toBe(true);
        const leer = { lieferantName: null, dokumente: [{ typ: 'SONSTIG', dokumentNummer: null, dokumentDatum: null, betragBrutto: null, dateiname: undefined as unknown as string }] } as Kette;
        expect(passtZurSuche(leer, 'sonstiges')).toBe(true);
    });
});

describe('TYP_LABELS', () => {
    it('hat für jeden Typ eine Beschriftung', () => {
        expect(TYP_LABELS.AUFTRAGSBESTAETIGUNG).toBe('AB');
        expect(TYP_LABELS.WERKSTOFFZEUGNIS).toBe('Werkstoffzeugnis');
        expect(Object.keys(TYP_LABELS)).toHaveLength(7);
    });
});
