/**
 * Vitest-Suite fuer helpers – Schwerpunkt: extractBoldFromHtml /
 * extractFontSizeFromHtml.
 *
 * Hintergrund (Regressions-Bug, 2026-05-21):
 *  Im Backend (RechnungPdfService#parseHtmlToElements) wird der DTO-Wert
 *  `fett` als `defaultBold` benutzt. Steht der auf true, wird JEDER
 *  Text-Chunk ausserhalb von <strong>/<span>-Tags fett gerendert – also
 *  praktisch die ganze Leistung.
 *
 *  `extractBoldFromHtml` liefert aber bereits dann true, wenn auch nur EIN
 *  Wort fett markiert ist. Daraus folgt:
 *   - Diese Funktion darf NICHT als Block-Default fuer Tiptap-HTML benutzt
 *     werden (TEXT- und SERVICE-Bloecke senden `fett: false` ans Backend,
 *     siehe index.tsx -> contentBlocks-Mapping).
 *
 * DSGVO: ausschliesslich Dummy-Daten.
 */
import { describe, it, expect } from 'vitest';
import {
    brauchtAnnahmeLinkAbfrage,
    buildPositionMap,
    calculateNetto,
    serviceLineTotal,
    calculateNettoNachRabatt,
    nettoNachGlobalRabatt,
    rabattBetrag,
    calculateSectionSubtotal,
    gruppiereFuerAnzeige,
    extractBoldFromHtml,
    extractFontSizeFromHtml,
    zahlungszielPlaceholderToChipHtml,
    chipHtmlToZahlungszielPlaceholder,
    ZAHLUNGSZIEL_PLACEHOLDER,
    ZAHLUNGSZIEL_TAGE_PLACEHOLDER,
    buildBezugsdokumentKontext,
    defaultsLabelKandidaten,
    ersetzeBezugsdokumentDatumPlatzhalter,
    mussAufBezugsdokumentWarten,
    mussAufKontextWarten,
    repariereLeeresBezugsdatumInStandardtext,
    parseBlocksAusPositionenJson,
    vergleicheLeistungen,
    formatiereDifferenzHinweis,
} from './helpers';
import type { DocBlock } from './types';

describe('extractBoldFromHtml', () => {
    it('liefert false fuer leeres oder undefiniertes HTML', () => {
        expect(extractBoldFromHtml('')).toBe(false);
        expect(extractBoldFromHtml(undefined as unknown as string)).toBe(false);
    });

    it('liefert false fuer reinen Text ohne Markup', () => {
        expect(extractBoldFromHtml('<p>Maler streichen Wand</p>')).toBe(false);
    });

    it('liefert true wenn EIN einzelnes Wort per <strong> fett markiert ist (Bug-Regression)', () => {
        // Genau dieses HTML kommt aus TiptapEditor, wenn der User nur ein
        // einzelnes Wort fett setzt. Vor dem Fix wurde dieser true-Wert
        // als block.fett ans Backend geschickt und liess die GANZE Leistung
        // fett werden.
        const html = '<p>Maler streicht <strong>weisse</strong> Wand</p>';
        expect(extractBoldFromHtml(html)).toBe(true);
    });

    it('erkennt auch <b>-Tag und font-weight im Style', () => {
        expect(extractBoldFromHtml('<p>Text <b>fett</b> mehr</p>')).toBe(true);
        expect(extractBoldFromHtml('<p><span style="font-weight: 700">fett</span></p>')).toBe(true);
    });
});

describe('extractFontSizeFromHtml', () => {
    it('liefert undefined wenn keine font-size im HTML steht', () => {
        expect(extractFontSizeFromHtml('<p>Plain Text Mustermann</p>')).toBeUndefined();
    });

    it('liefert die dominante (haeufigste) Groesse zurueck', () => {
        const html =
            '<p><span style="font-size: 12pt">Eins</span> ' +
            '<span style="font-size: 14pt">Zwei</span> ' +
            '<span style="font-size: 14pt">Drei</span></p>';
        expect(extractFontSizeFromHtml(html)).toBe(14);
    });

    it('clampt Werte ausserhalb 10-20pt', () => {
        expect(extractFontSizeFromHtml('<span style="font-size: 6pt">x</span>')).toBe(10);
        expect(extractFontSizeFromHtml('<span style="font-size: 48pt">x</span>')).toBe(20);
    });
});

/**
 * Zahlungsziel-Chips (Regressions-Bug, 2026-06-11):
 *  Das Zahlungsziel im Textbaustein stand als eingefrorener Klartext
 *  ("8 Tage" / "17.06.2026") im Editor statt als geschuetzter Chip.
 *  Beide Platzhalter ({{ZAHLUNGSZIEL}} = Faelligkeitsdatum,
 *  {{ZAHLUNGSZIEL_TAGE}} = Anzahl Tage) muessen verlustfrei in Chip-Spans
 *  und wieder zurueck wandelbar sein — sonst friert der Wert beim Speichern ein.
 */
describe('zahlungszielPlaceholderToChipHtml', () => {
    it('wandelt {{ZAHLUNGSZIEL}} in einen Datum-Chip', () => {
        const html = '<p>Zahlbar bis: {{ZAHLUNGSZIEL}}</p>';
        expect(zahlungszielPlaceholderToChipHtml(html, '17.06.2026', '8'))
            .toBe('<p>Zahlbar bis: <span data-zahlungsziel-chip="datum">17.06.2026</span></p>');
    });

    it('wandelt {{ZAHLUNGSZIEL_TAGE}} in einen Tage-Chip (Bug-Regression)', () => {
        const html = '<p>Zahlbar innerhalb von {{ZAHLUNGSZIEL_TAGE}} Tagen nach Rechnungsdatum.</p>';
        expect(zahlungszielPlaceholderToChipHtml(html, '17.06.2026', '8'))
            .toBe('<p>Zahlbar innerhalb von <span data-zahlungsziel-chip="tage">8</span> Tagen nach Rechnungsdatum.</p>');
    });

    it('verwechselt die beiden Platzhalter nicht, wenn beide vorkommen', () => {
        const html = '<p>{{ZAHLUNGSZIEL_TAGE}} Tage, faellig {{ZAHLUNGSZIEL}}</p>';
        const result = zahlungszielPlaceholderToChipHtml(html, '17.06.2026', '8');
        expect(result).toContain('<span data-zahlungsziel-chip="tage">8</span>');
        expect(result).toContain('<span data-zahlungsziel-chip="datum">17.06.2026</span>');
    });

    it('toleriert Leerraum und Kleinschreibung im Platzhalter', () => {
        const result = zahlungszielPlaceholderToChipHtml('<p>{{ zahlungsziel }}</p>', '17.06.2026', '8');
        expect(result).toBe('<p><span data-zahlungsziel-chip="datum">17.06.2026</span></p>');
    });
});

describe('chipHtmlToZahlungszielPlaceholder', () => {
    it('serialisiert den Datum-Chip zurueck zu {{ZAHLUNGSZIEL}}', () => {
        const html = '<p>Zahlbar bis: <span data-zahlungsziel-chip="datum">17.06.2026</span></p>';
        expect(chipHtmlToZahlungszielPlaceholder(html))
            .toBe(`<p>Zahlbar bis: ${ZAHLUNGSZIEL_PLACEHOLDER}</p>`);
    });

    it('serialisiert den Tage-Chip zurueck zu {{ZAHLUNGSZIEL_TAGE}} (Bug-Regression)', () => {
        const html = '<p>von <span data-zahlungsziel-chip="tage">8</span> Tagen</p>';
        expect(chipHtmlToZahlungszielPlaceholder(html))
            .toBe(`<p>von ${ZAHLUNGSZIEL_TAGE_PLACEHOLDER} Tagen</p>`);
    });

    it('interpretiert Legacy-Chips (data-zahlungsziel-chip="true") als Datum', () => {
        const html = '<p><span data-zahlungsziel-chip="true">17.06.2026</span></p>';
        expect(chipHtmlToZahlungszielPlaceholder(html))
            .toBe(`<p>${ZAHLUNGSZIEL_PLACEHOLDER}</p>`);
    });

    it('ist verlustfrei im Round-Trip Platzhalter -> Chip -> Platzhalter', () => {
        const original = '<p>Zahlbar innerhalb von {{ZAHLUNGSZIEL_TAGE}} Tagen, faellig am {{ZAHLUNGSZIEL}}.</p>';
        const chipped = zahlungszielPlaceholderToChipHtml(original, '17.06.2026', '8');
        expect(chipHtmlToZahlungszielPlaceholder(chipped)).toBe(original);
    });

    it('laesst HTML ohne Chips unveraendert', () => {
        const html = '<p>Mit freundlichen Gruessen, Max Mustermann</p>';
        expect(chipHtmlToZahlungszielPlaceholder(html)).toBe(html);
    });
});

/**
 * Bezugsdaten beim Umwandeln Angebot -> Auftragsbestaetigung:
 *  Der explizite Vorgaenger muss Nummer, Typ und Datum fuer die neuen
 *  Standard-Textbausteine liefern.
 */
describe('buildBezugsdokumentKontext', () => {
    it('uebernimmt beim umgewandelten Angebot auch das Bezugsdokumentdatum (Bug-Regression)', () => {
        expect(buildBezugsdokumentKontext({
            dokumentNummer: 'AG-2026/06/00006',
            datum: '2026-06-10',
        }, 'Angebot')).toEqual({
            bezugsdokument: 'AG-2026/06/00006',
            bezugsdokumentTyp: 'Angebot',
            bezugsdokumentDatum: '10.06.2026',
        });
    });

    it('liefert bei fehlendem Datum einen leeren Platzhalterwert', () => {
        expect(buildBezugsdokumentKontext({
            dokumentNummer: 'AG-2026/06/00007',
        }, 'Angebot').bezugsdokumentDatum).toBe('');
    });
});

describe('mussAufBezugsdokumentWarten', () => {
    it('wartet bei explizitem Vorgaenger bis Nummer, Typ und Datum vorhanden sind', () => {
        expect(mussAufBezugsdokumentWarten(42, {
            bezugsdokument: 'AG-2026/06/00008',
            bezugsdokumentTyp: 'Angebot',
            bezugsdokumentDatum: '',
        })).toBe(true);
    });

    it('gibt die Standardtexte frei, sobald der Vorgaenger vollstaendig geladen ist', () => {
        expect(mussAufBezugsdokumentWarten(42, {
            bezugsdokument: 'AG-2026/06/00008',
            bezugsdokumentTyp: 'Angebot',
            bezugsdokumentDatum: '12.06.2026',
        })).toBe(false);
    });

    it('wartet ohne expliziten Vorgaenger nicht auf Bezugsdaten', () => {
        expect(mussAufBezugsdokumentWarten(undefined, {})).toBe(false);
    });
});

describe('ersetzeBezugsdokumentDatumPlatzhalter', () => {
    it('ersetzt Schreibvarianten des Bezugsdatum-Platzhalters', () => {
        expect(ersetzeBezugsdokumentDatumPlatzhalter(
            '<p>Angebot vom {{ bezugsdokumentdatum }}</p>',
            '12.06.2026',
        )).toBe('<p>Angebot vom 12.06.2026</p>');
    });

    it('kann den frueheren Fehlerzustand mit leerem Datum reproduzieren', () => {
        expect(ersetzeBezugsdokumentDatumPlatzhalter(
            '<p>Angebot vom {{BEZUGSDOKUMENTDATUM}}.</p>',
            '',
        )).toBe('<p>Angebot vom .</p>');
    });
});

describe('repariereLeeresBezugsdatumInStandardtext', () => {
    const identity = (html: string) => html;

    it('repariert einen unveraenderten Standardtext aus dem frueheren Fehlerzustand', () => {
        expect(repariereLeeresBezugsdatumInStandardtext(
            '<p>Angebot vom .</p>',
            '<p>Angebot vom {{BEZUGSDOKUMENTDATUM}}.</p>',
            '12.06.2026',
            identity,
        )).toBe('<p>Angebot vom 12.06.2026.</p>');
    });

    it('laesst einen manuell bearbeiteten Standardtext unangetastet', () => {
        expect(repariereLeeresBezugsdatumInStandardtext(
            '<p>Individuell bearbeiteter Bezug.</p>',
            '<p>Angebot vom {{BEZUGSDOKUMENTDATUM}}.</p>',
            '12.06.2026',
            identity,
        )).toBe('<p>Individuell bearbeiteter Bezug.</p>');
    });
});

/**
 * Fallback-Reihenfolge fuer Standard-Textbausteine: Nachtragsangebote ohne
 * eigene Vorlage/Defaults greifen auf die Angebots-Defaults zurueck.
 */
describe('defaultsLabelKandidaten', () => {
    it('liefert fuer Nachtragsangebot den Angebots-Fallback (Bug-Regression)', () => {
        expect(defaultsLabelKandidaten('NACHTRAGSANGEBOT', 'Nachtragsangebot'))
            .toEqual(['Nachtragsangebot', 'Angebot']);
    });

    it('liefert fuer alle anderen Typen nur das eigene Label', () => {
        expect(defaultsLabelKandidaten('ANGEBOT', 'Angebot')).toEqual(['Angebot']);
        expect(defaultsLabelKandidaten('ABSCHLAGSRECHNUNG', 'Abschlagsrechnung')).toEqual(['Abschlagsrechnung']);
    });
});

/**
 * Gate fuer das Laden der Standard-Textbausteine (Regressions-Bug, 2026-06-12):
 *  Ein Angebot aus einem Projekt OHNE Kunde/Auftragsnummer bekam nie
 *  Vor-/Nachtexte, weil das Gate dauerhaft auf "Kontext bereit" wartete.
 *  Aus einer Anfrage (immer mit Kundenname) funktionierte es.
 */
describe('mussAufKontextWarten', () => {
    it('blockiert NICHT mehr, wenn der Kontext-Load abgeschlossen ist aber leer blieb (Bug-Regression)', () => {
        expect(mussAufKontextWarten({}, true, true)).toBe(false);
    });

    it('wartet, solange der Kontext-Load noch laeuft und keine Daten da sind', () => {
        expect(mussAufKontextWarten({}, false, true)).toBe(true);
    });

    it('blockiert nicht, sobald Kontext-Daten da sind (auch wenn Load formal noch laeuft)', () => {
        expect(mussAufKontextWarten({ kundenName: 'Max Mustermann' }, false, true)).toBe(false);
        expect(mussAufKontextWarten({ projektnummer: '2026/01/00001' }, false, true)).toBe(false);
    });

    it('wartet nie, wenn das Dokument weder Projekt noch Anfrage hat', () => {
        expect(mussAufKontextWarten({}, false, false)).toBe(false);
    });
});

/**
 * Gueltigkeitsdauer-Dialog vor dem Mailversand (Bug, 2026-08-06):
 *  Ein bereits digital angenommenes Angebot liess sich erneut versenden und
 *  bekam dabei einen frischen Annahme-Link. Das Backend stellt jetzt keinen
 *  zweiten Link mehr aus — dann darf der Anwender vorher auch nicht mehr nach
 *  der Gueltigkeit eines Links gefragt werden, den es gar nicht gibt.
 */
describe('brauchtAnnahmeLinkAbfrage', () => {
    it('fragt bei einem noch offenen Angebot nach der Gueltigkeit', () => {
        expect(brauchtAnnahmeLinkAbfrage('ANGEBOT', false, false)).toBe(true);
        expect(brauchtAnnahmeLinkAbfrage('NACHTRAGSANGEBOT', false, undefined)).toBe(true);
    });

    it('fragt NICHT mehr, wenn der Kunde das Angebot bereits angenommen hat', () => {
        expect(brauchtAnnahmeLinkAbfrage('ANGEBOT', false, true)).toBe(false);
        expect(brauchtAnnahmeLinkAbfrage('NACHTRAGSANGEBOT', false, true)).toBe(false);
    });

    it('fragt nicht beim Entwurfs-Versand — dort gibt es nie einen Annahme-Link', () => {
        expect(brauchtAnnahmeLinkAbfrage('ANGEBOT', true, false)).toBe(false);
    });

    it('fragt nicht bei Dokumenten ohne digitale Annahme', () => {
        expect(brauchtAnnahmeLinkAbfrage('RECHNUNG', false, false)).toBe(false);
        expect(brauchtAnnahmeLinkAbfrage('AUFTRAGSBESTAETIGUNG', false, false)).toBe(false);
    });
});

const leistung = (id: string, preis: number, extra: Partial<DocBlock> = {}): DocBlock => ({
    id, type: 'SERVICE', title: `Leistung ${id}`, quantity: 1, unit: 'Stk', price: preis, ...extra,
});

describe('buildPositionMap mit Alternativgruppen', () => {
    it('gibt Varianten derselben Gruppe eine Nummer mit Buchstaben', () => {
        const blocks = [
            leistung('a', 100),
            leistung('b', 200, { optional: true, alternativGruppe: 'Geländer' }),
            leistung('c', 300, { optional: true, alternativGruppe: 'Geländer' }),
            leistung('d', 400),
        ];
        const map = buildPositionMap(blocks);
        expect(map.get('a')).toBe('1');
        expect(map.get('b')).toBe('2a');
        expect(map.get('c')).toBe('2b');
        expect(map.get('d')).toBe('3');
    });

    it('nummeriert Gruppen innerhalb eines Bauabschnitts mit Praefix', () => {
        const blocks: DocBlock[] = [{
            id: 'sec', type: 'SECTION_HEADER', sectionLabel: 'Stahlbau', children: [
                leistung('k1', 100),
                leistung('k2', 200, { optional: true, alternativGruppe: 'Geländer' }),
                leistung('k3', 300, { optional: true, alternativGruppe: 'Geländer' }),
                leistung('k4', 400),
            ],
        }];
        const map = buildPositionMap(blocks);
        expect(map.get('sec')).toBe('1.0');
        expect(map.get('k1')).toBe('1.1');
        expect(map.get('k2')).toBe('1.2a');
        expect(map.get('k3')).toBe('1.2b');
        expect(map.get('k4')).toBe('1.3');
    });

    it('zwei Gruppen im selben Dokument bekommen eigene Nummern', () => {
        const blocks = [
            leistung('g1', 100, { optional: true, alternativGruppe: 'Geländer' }),
            leistung('g2', 200, { optional: true, alternativGruppe: 'Geländer' }),
            leistung('t1', 300, { optional: true, alternativGruppe: 'Treppe' }),
            leistung('t2', 400, { optional: true, alternativGruppe: 'Treppe' }),
        ];
        const map = buildPositionMap(blocks);
        expect(map.get('g1')).toBe('1a');
        expect(map.get('g2')).toBe('1b');
        expect(map.get('t1')).toBe('2a');
        expect(map.get('t2')).toBe('2b');
    });
});

describe('Basisbetrag schliesst Gruppenmitglieder aus', () => {
    it('calculateNetto zaehlt weder Optional noch Alternative', () => {
        const blocks = [
            leistung('fest', 1000),
            leistung('opt', 500, { optional: true }),
            leistung('alt', 700, { optional: true, alternativGruppe: 'Geländer' }),
        ];
        expect(calculateNetto(blocks)).toBe(1000);
    });

    it('calculateSectionSubtotal zaehlt weder Optional noch Alternative', () => {
        const section: DocBlock = {
            id: 'sec', type: 'SECTION_HEADER', children: [
                leistung('fest', 1000),
                leistung('alt', 700, { optional: true, alternativGruppe: 'Geländer' }),
            ],
        };
        expect(calculateSectionSubtotal(section)).toBe(1000);
    });
});

describe('gruppiereFuerAnzeige', () => {
    it('fasst aufeinanderfolgende Varianten zu einem Eintrag zusammen', () => {
        const blocks = [
            leistung('a', 100),
            leistung('b', 200, { optional: true, alternativGruppe: 'Geländer' }),
            leistung('c', 300, { optional: true, alternativGruppe: 'Geländer' }),
            leistung('d', 400, { optional: true }),
        ];
        const eintraege = gruppiereFuerAnzeige(blocks);

        expect(eintraege).toHaveLength(3);
        expect(eintraege[0]).toMatchObject({ art: 'block' });
        expect(eintraege[1]).toMatchObject({ art: 'gruppe', name: 'Geländer' });
        expect(eintraege[1].art === 'gruppe' && eintraege[1].positionen.map(p => p.id))
            .toEqual(['b', 'c']);
        expect(eintraege[2]).toMatchObject({ art: 'block' });
    });

    it('loest Bauabschnitte nicht auf', () => {
        const blocks: DocBlock[] = [{ id: 'sec', type: 'SECTION_HEADER', children: [] }];
        expect(gruppiereFuerAnzeige(blocks)).toEqual([{ art: 'block', block: blocks[0] }]);
    });
});

/**
 * Regressions-Bug (2026-08-28): Der Dokument-Pauschalrabatt (globalRabatt) wurde
 * nur in positionenJson als Metadatum abgelegt und im Editor-Footer sowie im PDF
 * angewandt — NIE in den persistierten `betragNetto`. Folge: Projekt-Dokumentliste,
 * Offene Posten, Projekt-Bruttopreis und Rechnungs-E-Mail zeigten den
 * unrabattierten Betrag.
 *
 * DSGVO: ausschliesslich Dummy-Daten.
 */
describe('calculateNettoNachRabatt', () => {
    it('zieht den Dokument-Pauschalrabatt von der Nettosumme ab', () => {
        const blocks = [leistung('a', 3412.2)];
        expect(calculateNettoNachRabatt(blocks, 3)).toBeCloseTo(3309.83, 2);
    });

    it('laesst die Summe ohne Rabatt unveraendert', () => {
        const blocks = [leistung('a', 1000)];
        expect(calculateNettoNachRabatt(blocks, 0)).toBe(1000);
        expect(calculateNettoNachRabatt(blocks, undefined)).toBe(1000);
    });

    it('kombiniert Positions-Rabatt und Dokument-Rabatt', () => {
        // Position: 1000 − 10% = 900, danach Dokument-Rabatt 5% = 855
        const blocks = [leistung('a', 1000, { discount: 10 })];
        expect(calculateNettoNachRabatt(blocks, 5)).toBeCloseTo(855, 2);
    });

    it('ignoriert optionale und Alternativ-Positionen wie calculateNetto', () => {
        const blocks = [
            leistung('fest', 1000),
            leistung('opt', 500, { optional: true }),
            leistung('alt', 700, { optional: true, alternativGruppe: 'Geländer' }),
        ];
        expect(calculateNettoNachRabatt(blocks, 10)).toBeCloseTo(900, 2);
    });

    it('behandelt 100% Rabatt und ignoriert unplausible Werte', () => {
        const blocks = [leistung('a', 1000)];
        expect(calculateNettoNachRabatt(blocks, 100)).toBe(0);
        expect(calculateNettoNachRabatt(blocks, -5)).toBe(1000);
        expect(calculateNettoNachRabatt(blocks, 150)).toBe(0);
    });
});

/**
 * Frontend und Backend MUESSEN den Pauschalrabatt identisch runden. Wich der
 * gespeicherte Betrag um einen Cent vom PDF ab, hielt der Korrekturlauf ein
 * korrektes Dokument fuer falsch und schrieb eine festgeschriebene Rechnung um.
 * Pendant: RabattRechnerTest im Backend.
 *
 * DSGVO: ausschliesslich Dummy-Daten.
 */
describe('rabattBetrag / nettoNachGlobalRabatt', () => {
    // Diese Faelle trifft die Binaerdarstellung von unten (4,10*15 = 61.49999999999999).
    // Eine Rundung direkt auf dem Double-Produkt liefert hier einen Cent zu wenig und
    // weicht damit vom BigDecimal-Backend ab. Der frueher benutzte 100,10/5%-Fall ist
    // ausgerechnet einer, bei dem beide Varianten uebereinstimmen — er taugt nicht als
    // Waechter.
    it.each([
        [4.10, 15, 0.62, 3.48],
        [32.30, 15, 4.85, 27.45],
        [2.30, 25, 0.58, 1.72],
        [2048.70, 15, 307.31, 1741.39],
    ])('rechnet %s EUR mit %s%% exakt wie das Backend', (netto, prozent, erwarteterRabatt, erwartetesNetto) => {
        expect(rabattBetrag(netto, prozent)).toBeCloseTo(erwarteterRabatt, 2);
        expect(nettoNachGlobalRabatt(netto, prozent)).toBeCloseTo(erwartetesNetto, 2);
    });

    it('rundet die Haelfte vom Nullpunkt weg, auch bei negativen Betraegen', () => {
        // Java RoundingMode.HALF_UP rundet away-from-zero, Math.round Richtung +unendlich.
        expect(rabattBetrag(-100.1, 5)).toBeCloseTo(-5.01, 2);
    });

    it('rundet den Rabattbetrag auf zwei Nachkommastellen (Abzugs-Variante)', () => {
        // round2(100,10 × 5 / 100) = round2(5,005) = 5,01 -> 95,09
        expect(rabattBetrag(100.1, 5)).toBeCloseTo(5.01, 2);
        expect(nettoNachGlobalRabatt(100.1, 5)).toBeCloseTo(95.09, 2);
        // Die Faktor-Variante haette 95,10 geliefert — genau die Divergenz, die der
        // Korrekturlauf sonst als "falsch" interpretiert haette.
        expect(nettoNachGlobalRabatt(100.1, 5)).not.toBeCloseTo(95.1, 2);
    });

    it('trifft den Realfall aus dem Bugreport', () => {
        expect(nettoNachGlobalRabatt(3412.2, 3)).toBeCloseTo(3309.83, 2);
    });

    it('laesst den Betrag ohne wirksamen Rabatt unveraendert', () => {
        expect(nettoNachGlobalRabatt(1000, 0)).toBe(1000);
        expect(nettoNachGlobalRabatt(1000, undefined)).toBe(1000);
        expect(nettoNachGlobalRabatt(1000, -5)).toBe(1000);
        expect(rabattBetrag(1000, 0)).toBe(0);
    });

    it('kappt unplausible Prozentwerte bei 100', () => {
        expect(nettoNachGlobalRabatt(1000, 100)).toBe(0);
        expect(nettoNachGlobalRabatt(1000, 150)).toBe(0);
    });
});

/**
 * Teilrechnung: Der Restbetrag leitet sich aus dem RABATTIERTEN betragNetto des
 * Basisdokuments ab. Eine Teilrechnung ueber alle Positionen muss deshalb denselben
 * rabattierten Betrag ergeben — sonst uebersteigt sie den Restbetrag und wird
 * clientseitig gesperrt bzw. serverseitig abgewiesen.
 */
describe('Teilrechnung erbt den Pauschalrabatt', () => {
    it('Vollauswahl trifft exakt den rabattierten Restbetrag des Basisdokuments', () => {
        const blocks = [leistung('a', 3412.2)];
        const basisBetragNetto = calculateNettoNachRabatt(blocks, 3); // = 3309,83
        const teilrechnungSumme = nettoNachGlobalRabatt(
            blocks.reduce((sum, b) => sum + (b.quantity || 0) * (b.price || 0), 0), 3);

        expect(teilrechnungSumme).toBeCloseTo(basisBetragNetto, 2);
        expect(teilrechnungSumme).toBeLessThanOrEqual(basisBetragNetto + 0.01);
    });
});

/**
 * Zeilenrundung: Editor und Backfill MUESSEN pro Position denselben Cent-Betrag
 * ergeben. Vorher summierte das Frontend ungerundete Doubles und das Backend
 * rundete erst die Gesamtsumme — 10,10 EUR mit 5 % Positionsrabatt ergab 9,59
 * gegen 9,60. Der Korrekturlauf haette solche Dokumente fuer falsch gehalten.
 * Pendant: AusgangsGeschaeftsDokumentServiceTest#rundet_jede_zeile_wie_das_frontend.
 *
 * DSGVO: ausschliesslich Dummy-Daten.
 */
describe('serviceLineTotal rundet pro Zeile auf Cent', () => {
    it.each([
        [10.10, 5, 9.60],
        [4.10, 15, 3.49],
        [2.30, 25, 1.73],
        [200.09, 10, 180.08],
        // Rabattsaetze mit DREI Nachkommastellen: `rabatt * 100` im Double verfehlt
        // exakte Ties (1.005 * 100 = 100.49999999999999) und rundet ab, wo das
        // Backend aufrundet. Bei 10.000 EUR sind das 1,00 EUR Differenz.
        [10000.00, 1.005, 9899.00],
        [899.90, 0.145, 898.55],
    ])('%s EUR mit %s%% Positionsrabatt ergibt %s', (preis, discount, erwartet) => {
        expect(serviceLineTotal(leistung('x', preis, { discount }))).toBeCloseTo(erwartet, 2);
    });

    it('laesst Zeilen ohne Rabatt unveraendert', () => {
        expect(serviceLineTotal(leistung('x', 1000))).toBe(1000);
    });
});

/**
 * Pauschalrabatt mit Saetzen, die im Double keine exakte Multiplikation zulassen.
 * Pendant: RabattRechnerTest#trifft_saetze_mit_nachkommastellen.
 */
describe('rabattBetrag trifft Saetze mit Nachkommastellen', () => {
    it.each([
        [27382.50, 21.4, 5859.86],
        [1000.00, 16.4, 164.00],
        [12345.67, 19.9, 2456.79],
    ])('%s EUR mit %s%% ergibt %s', (netto, prozent, erwartet) => {
        expect(rabattBetrag(netto, prozent)).toBeCloseTo(erwartet, 2);
    });
});

/**
 * Positionsvergleich Auftragsbestaetigung <-> Schlussrechnung.
 *
 * Benennt in der Differenzzeile, welche Leistungen entfallen bzw. dazugekommen
 * sind. Rein beschreibend — der Differenzbetrag entsteht unabhaengig davon als
 * Ausgleichsgroesse, damit die Spalte auf der Rechnung immer aufgeht.
 */
describe('vergleicheLeistungen', () => {
    const ab = [
        leistung('geruest', 2000, { title: 'Gerüststellung' }),
        leistung('putz', 6000, { title: 'Putzarbeiten' }),
        leistung('reinigung', 2000, { title: 'Bauendreinigung' }),
    ];

    it('nennt die aus der Schlussrechnung geloeschte Leistung', () => {
        const schlussrechnung = ab.filter(b => b.id !== 'geruest');

        const { entfallen, zusaetzlich } = vergleicheLeistungen(ab, schlussrechnung);

        expect(entfallen).toEqual(['Gerüststellung']);
        expect(zusaetzlich).toEqual([]);
    });

    it('nennt unvorhergesehene Zusatzleistungen', () => {
        const schlussrechnung = [...ab, leistung('bohrung', 1000, { title: 'Kernbohrung Kellerwand' })];

        const { entfallen, zusaetzlich } = vergleicheLeistungen(ab, schlussrechnung);

        expect(entfallen).toEqual([]);
        expect(zusaetzlich).toEqual(['Kernbohrung Kellerwand']);
    });

    it('erkennt beide Richtungen gleichzeitig', () => {
        const schlussrechnung = [
            ...ab.filter(b => b.id !== 'geruest'),
            leistung('bohrung', 1000, { title: 'Kernbohrung Kellerwand' }),
        ];

        const { entfallen, zusaetzlich } = vergleicheLeistungen(ab, schlussrechnung);

        expect(entfallen).toEqual(['Gerüststellung']);
        expect(zusaetzlich).toEqual(['Kernbohrung Kellerwand']);
    });

    it('findet Leistungen auch innerhalb von Bauabschnitten', () => {
        const mitAbschnitt: DocBlock[] = [{
            id: 'abschnitt', type: 'SECTION_HEADER', sectionLabel: 'Rohbau',
            children: [leistung('geruest', 2000, { title: 'Gerüststellung' })],
        }];

        const { entfallen } = vergleicheLeistungen(mitAbschnitt, []);

        expect(entfallen).toEqual(['Gerüststellung']);
    });

    it('ignoriert optionale und Alternativ-Positionen', () => {
        // Sie zaehlen auch nicht in die Nettosumme — in der Auflistung waeren sie nur Rauschen.
        const mitOptional = [
            ...ab,
            leistung('extra', 500, { title: 'Zusatzanstrich', optional: true }),
            leistung('variante', 900, { title: 'Geländer Edelstahl', optional: true, alternativGruppe: 'Geländer' }),
        ];

        const { entfallen } = vergleicheLeistungen(mitOptional, ab);

        expect(entfallen).toEqual([]);
    });

    it('meldet keine Aenderung, wenn nur die Menge angepasst wurde', () => {
        // Die Mengenaenderung schlaegt sich im Differenzbetrag nieder, nicht in der Namensliste.
        const schlussrechnung = ab.map(b => b.id === 'putz' ? { ...b, quantity: 0.5 } : b);

        const { entfallen, zusaetzlich } = vergleicheLeistungen(ab, schlussrechnung);

        expect(entfallen).toEqual([]);
        expect(zusaetzlich).toEqual([]);
    });

    it('faellt bei Positionen ohne Titel auf einen Platzhalter zurueck', () => {
        const ohneTitel: DocBlock[] = [{ id: 'x', type: 'SERVICE', quantity: 1, price: 100 }];

        expect(vergleicheLeistungen(ohneTitel, []).entfallen).toEqual(['Ohne Bezeichnung']);
    });
});

describe('formatiereDifferenzHinweis', () => {
    it('gibt bei leerer Liste keinen Hinweis', () => {
        expect(formatiereDifferenzHinweis([])).toBe('');
    });

    it('zaehlt kurze Listen vollstaendig auf', () => {
        expect(formatiereDifferenzHinweis(['Gerüststellung', 'Bauendreinigung']))
            .toBe('Gerüststellung, Bauendreinigung');
    });

    it('kuerzt lange Listen, damit die Zeile auf der Rechnung nicht umbricht', () => {
        const namen = ['Eins', 'Zwei', 'Drei', 'Vier', 'Fünf', 'Sechs'];

        expect(formatiereDifferenzHinweis(namen)).toBe('Eins, Zwei, Drei, Vier u. a.');
    });
});

describe('parseBlocksAusPositionenJson', () => {
    it('liest das heutige Format mit blocks-Feld', () => {
        const json = JSON.stringify({ blocks: [leistung('a', 100)], globalRabatt: 5 });

        expect(parseBlocksAusPositionenJson(json)).toHaveLength(1);
    });

    it('liest das alte Format als reines Block-Array', () => {
        const json = JSON.stringify([leistung('a', 100), leistung('b', 200)]);

        expect(parseBlocksAusPositionenJson(json)).toHaveLength(2);
    });

    it.each([
        ['null', null],
        ['undefined', undefined],
        ['leerer String', ''],
        ['kaputtes JSON', '{nicht wirklich json'],
        ['Objekt ohne blocks', '{"globalRabatt":5}'],
    ])('liefert bei %s eine leere Liste statt zu werfen', (_bezeichnung, wert) => {
        expect(parseBlocksAusPositionenJson(wert as string | null | undefined)).toEqual([]);
    });
});
