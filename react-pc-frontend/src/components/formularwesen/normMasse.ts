import type { FormBlock } from '../../types';

/**
 * Briefbogen-Maße nach DIN 5008 / DIN 676 für den Vorlagen-Editor.
 *
 * Der Editor rechnet intern in pt (DIN A4 = 595 × 842 pt, Ursprung oben links) –
 * genau so speichert er die Vorlagen, und das Backend übernimmt die Werte 1:1
 * als PDF-Punkte (1 pt = 1/72 Zoll). Die Norm ist in mm angegeben.
 */

/** PDF-Punkte je mm: 72 pt pro Zoll / 25,4 mm ≈ 2,8346. */
export const PT_JE_MM = 72 / 25.4;

export const mmZuPt = (mm: number): number => mm * PT_JE_MM;
export const ptZuMm = (pt: number): number => pt / PT_JE_MM;

/**
 * mm-Eingabe → pt auf zwei Nachkommastellen. So bleiben die gespeicherten
 * Zahlen kurz, und 20 mm werden beim Zurückrechnen wieder zu „20,0“.
 */
export const mmZuPtGerundet = (mm: number): number => Math.round(mmZuPt(mm) * 100) / 100;

/** pt → mm-Anzeige mit einer Nachkommastelle und deutschem Komma, z. B. „45,0“. */
export const formatiereMm = (pt: number): string =>
    ptZuMm(pt).toLocaleString('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

/**
 * Briefform nach DIN 5008: Form B ist der übliche Geschäftsbrief (Anschrift
 * weiter unten, mehr Platz für den Briefkopf), Form A setzt die Anschrift höher.
 */
export type Briefform = 'A' | 'B';
export const STANDARD_BRIEFFORM: Briefform = 'B';

export interface RechteckMm {
    x: number;
    y: number;
    breite: number;
    hoehe: number;
}

export interface NormMasse {
    /** Ganzes Anschriftfeld 85 × 45 mm (Zusatz- und Vermerkzone + Anschriftzone). */
    anschriftfeld: RechteckMm;
    /** Obere 17,7 mm: Rücksendeangabe und Vermerke (5 Zeilen). */
    zusatzzone: RechteckMm;
    /** Untere 27,3 mm: die eigentliche Anschrift (9 Zeilen). */
    anschriftzone: RechteckMm;
    /** Sichtfenster eines DIN-lang-Umschlags (90 × 45 mm) über dem Anschriftfeld. */
    fenster: RechteckMm;
    /** Informationsblock rechts (Datum, Nummern, Ansprechpartner). */
    infoblock: RechteckMm;
    /** Falzmarken am linken Rand, Abstand von oben in mm. */
    falzmarken: number[];
    /** Lochmarke am linken Rand (Blattmitte), Abstand von oben in mm. */
    lochmarke: number;
}

const ANSCHRIFTFELD_LINKS = 20;
const ANSCHRIFTFELD_BREITE = 85;
const ANSCHRIFTFELD_HOEHE = 45;
const ZUSATZZONE_HOEHE = 17.7;
const FENSTER_BREITE = 90;
const FENSTER_HOEHE = 45;
const INFOBLOCK_LINKS = 125;
const INFOBLOCK_BREITE = 75;
const LOCHMARKE = 148.5;

const FORM_WERTE: Record<Briefform, { feldOben: number; infoOben: number; falzmarken: number[] }> = {
    A: { feldOben: 27, infoOben: 32, falzmarken: [87, 192] },
    B: { feldOben: 45, infoOben: 50, falzmarken: [105, 210] },
};

/** Alle Norm-Maße für die gewählte Briefform in mm. */
export function normMasse(form: Briefform): NormMasse {
    const { feldOben, infoOben, falzmarken } = FORM_WERTE[form];
    const feldUnten = feldOben + ANSCHRIFTFELD_HOEHE;
    return {
        anschriftfeld: { x: ANSCHRIFTFELD_LINKS, y: feldOben, breite: ANSCHRIFTFELD_BREITE, hoehe: ANSCHRIFTFELD_HOEHE },
        zusatzzone: { x: ANSCHRIFTFELD_LINKS, y: feldOben, breite: ANSCHRIFTFELD_BREITE, hoehe: ZUSATZZONE_HOEHE },
        anschriftzone: {
            x: ANSCHRIFTFELD_LINKS,
            y: feldOben + ZUSATZZONE_HOEHE,
            breite: ANSCHRIFTFELD_BREITE,
            hoehe: ANSCHRIFTFELD_HOEHE - ZUSATZZONE_HOEHE,
        },
        fenster: { x: ANSCHRIFTFELD_LINKS, y: feldOben, breite: FENSTER_BREITE, hoehe: FENSTER_HOEHE },
        // Der Infoblock endet auf Höhe der Unterkante des Anschriftfelds.
        infoblock: { x: INFOBLOCK_LINKS, y: infoOben, breite: INFOBLOCK_BREITE, hoehe: feldUnten - infoOben },
        falzmarken: [...falzmarken],
        lochmarke: LOCHMARKE,
    };
}

export interface RechteckPt {
    x: number;
    y: number;
    width: number;
    height: number;
}

export const rechteckInPt = (r: RechteckMm): RechteckPt => ({
    x: mmZuPt(r.x),
    y: mmZuPt(r.y),
    width: mmZuPt(r.breite),
    height: mmZuPt(r.hoehe),
});

/** Rundungsspielraum: Blöcke liegen auf ganzen pt, die Norm nicht. */
const TOLERANZ_PT = 0.5;

const liegtIn = (block: RechteckPt, rahmen: RechteckPt): boolean =>
    block.x >= rahmen.x - TOLERANZ_PT
    && block.y >= rahmen.y - TOLERANZ_PT
    && block.x + block.width <= rahmen.x + rahmen.width + TOLERANZ_PT
    && block.y + block.height <= rahmen.y + rahmen.height + TOLERANZ_PT;

const ANSCHRIFT_PLATZHALTER = '{{KUNDENADRESSE}}';

/**
 * Der Baustein, der im Brief die Anschrift trägt: ein Kundenadresse-Feld oder
 * ein Text mit {{KUNDENADRESSE}}. Nur Seite 1 zählt – nur dort sitzt das Fenster.
 */
export function findeAnschriftBlock(items: FormBlock[]): FormBlock | null {
    const seite1 = items.filter(i => i.page === 1);
    return seite1.find(i => i.type === 'adresse')
        ?? seite1.find(i => i.type !== 'table' && i.type !== 'logo' && (i.content ?? '').includes(ANSCHRIFT_PLATZHALTER))
        ?? null;
}

/** Liegt der Block vollständig in der Anschriftzone der gewählten Briefform? */
export function anschriftImFenster(block: Pick<FormBlock, 'x' | 'y' | 'width' | 'height'>, form: Briefform): boolean {
    return liegtIn(block, rechteckInPt(normMasse(form).anschriftzone));
}

/**
 * Ergebnis der Prüfung:
 * - `passt`: Anschrift liegt in der Anschriftzone.
 * - `zusatzzone`: Anschrift liegt im Fenster, reicht aber in die Zone für Rücksendeangabe/Vermerke.
 * - `ragt-heraus`: Anschrift liegt (teilweise) außerhalb des Anschriftfelds – im Umschlag verdeckt.
 * - `keine-anschrift`: Kein Anschrift-Baustein auf Seite 1.
 */
export type AnschriftStatus = 'passt' | 'zusatzzone' | 'ragt-heraus' | 'keine-anschrift';

export function pruefeAnschrift(items: FormBlock[], form: Briefform): AnschriftStatus {
    const block = findeAnschriftBlock(items);
    if (!block) return 'keine-anschrift';
    if (anschriftImFenster(block, form)) return 'passt';
    if (liegtIn(block, rechteckInPt(normMasse(form).anschriftfeld))) return 'zusatzzone';
    return 'ragt-heraus';
}

/**
 * Norm-Position und -Größe der Anschriftzone in ganzen pt, nach innen gerundet –
 * so liegt der Block sicher in der Zone, auch nach dem Speichern.
 */
export function anschriftInsFenster(form: Briefform): RechteckPt {
    const zone = rechteckInPt(normMasse(form).anschriftzone);
    const links = Math.ceil(zone.x);
    const oben = Math.ceil(zone.y);
    const rechts = Math.floor(zone.x + zone.width);
    const unten = Math.floor(zone.y + zone.height);
    return { x: links, y: oben, width: rechts - links, height: unten - oben };
}

/** Infoblock-Position in ganzen pt (für den Baustein „Nummer & Datum“). */
export function infoblockPosition(form: Briefform): RechteckPt {
    const block = rechteckInPt(normMasse(form).infoblock);
    return { x: Math.ceil(block.x), y: Math.ceil(block.y), width: Math.floor(block.width), height: Math.floor(block.height) };
}
