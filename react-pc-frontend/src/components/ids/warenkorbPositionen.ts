import type { DocBlock } from '../document-editor/types';
import { formatDecimalInput, validateDecimalInput } from '../../lib/numberInput';
import { escapeHtml } from '../../lib/htmlSanitizer';
import { aufCent } from '../../lib/mwst';
import type { IdsArtikelstammAuftrag, IdsAufschlagQuelle, IdsPosition } from './api';
import type { ArtikelVerrechnungseinheit, ArtikelVorbelegung } from '../CreateArticleModal';

/**
 * Reine Rechenhilfen für den Warenkorb aus dem Lieferanten-Shop:
 * Verkaufspreis aus Einkaufspreis und Aufschlag, Summen und der Bau der
 * Dokument-Positionen („jeder Artikel eine Position“ bzw. „alles in einer“).
 *
 * <p>Ohne React und ohne Netz – dadurch vollständig testbar. Der Dialog hält
 * nur die Eingabe-Entwürfe und ruft diese Funktionen auf.</p>
 */

/** VK je Einheit = EK × (1 + Aufschlag/100), kaufmännisch auf Cent gerundet (`aufCent` aus `lib/mwst.ts`). */
export function verkaufspreis(einkaufspreis: number, aufschlagProzent: number): number {
    return aufCent(einkaufspreis * (1 + aufschlagProzent / 100));
}

/** Menge im deutschen Format ohne unnötige Nullen: 5 → „5“, 2,5 → „2,5“, 1000 → „1.000“. */
export function formatMenge(menge: number): string {
    return menge.toLocaleString('de-DE', { maximumFractionDigits: 3 });
}

/** Kleiner Hinweis, woher der vorbelegte Aufschlag stammt. */
export function aufschlagQuelleText(quelle: IdsAufschlagQuelle): string {
    switch (quelle) {
        case 'ARTIKEL': return 'aus Artikelstamm';
        case 'KATEGORIE': return 'aus Kategorie';
        case 'ALLGEMEIN': return 'Standard';
        default: return 'kein Aufschlag hinterlegt';
    }
}

/**
 * Woher eine Zeile stammt: aus welchem Shop-Besuch (`vorgangId`) und an welcher
 * Stelle sie dort stand (`index`). Holt man mehrmals Artikel aus dem Shop,
 * kann derselbe Index in mehreren Besuchen vorkommen – erst beides zusammen
 * ist eindeutig ({@link zeilenSchluessel}).
 */
export interface ZeilenHerkunft {
    vorgangId: number;
    index: number;
}

/** Eindeutiger Schlüssel einer Zeile über alle Shop-Besuche hinweg, z. B. „42:0“. */
export function zeilenSchluessel(herkunft: ZeilenHerkunft): string {
    return `${herkunft.vorgangId}:${herkunft.index}`;
}

/** Eine Zeile, wie der Dialog sie bearbeitet: Zahlen als Text-Entwürfe mit Dezimalkomma. */
export interface ZeilenEntwurf extends ZeilenHerkunft {
    artikelnummer: string;
    bezeichnung: string;
    menge: string;
    einheit: string;
    einkaufspreis: string;
    aufschlag: string;
    artikelId: number | null;
    /** Langtext des Shops (reiner Text), kommt zusätzlich in den Kundentext. */
    langtext: string | null;
}

/** Eine fertig geprüfte Zeile mit Zahlen. */
export interface WarenkorbZeile extends ZeilenHerkunft {
    artikelnummer: string;
    bezeichnung: string;
    menge: number;
    einheit: string;
    einkaufspreis: number;
    aufschlagProzent: number;
    artikelId: number | null;
    /** Langtext des Shops (reiner Text), kommt zusätzlich in den Kundentext. */
    langtext: string | null;
}

/** Zahl als Eingabe-Entwurf („12,5“), `null` wird zu leer. */
export function zuEntwurf(wert: number | null | undefined): string {
    return wert == null || !Number.isFinite(wert) ? '' : formatDecimalInput(wert);
}

/** Startzustand einer Zeile aus der Antwort des Servers zu einem Shop-Besuch. */
export function zeilenEntwurfAus(position: IdsPosition, vorgangId: number): ZeilenEntwurf {
    return {
        vorgangId,
        index: position.index,
        artikelnummer: position.artikelnummer,
        bezeichnung: position.bezeichnung,
        menge: zuEntwurf(position.menge),
        einheit: position.einheit,
        einkaufspreis: zuEntwurf(position.einkaufspreis),
        aufschlag: zuEntwurf(position.aufschlagProzent ?? 0),
        artikelId: position.stamm?.artikelId ?? null,
        langtext: position.langtext,
    };
}

/** Zahl aus einem Entwurf – `null`, solange er (noch) keine gültige Zahl ist. Für Live-Anzeigen. */
export function zahlAusEntwurf(entwurf: string): number | null {
    const ergebnis = validateDecimalInput(entwurf, { label: 'Wert', required: true });
    return ergebnis.valid ? ergebnis.value : null;
}

export type PruefErgebnis =
    | { gueltig: true; zeilen: WarenkorbZeile[] }
    | { gueltig: false; meldung: string };

/**
 * Prüft alle Zeilen der Liste vollständig, bevor etwas ins Dokument geht.
 * Was nicht ins Dokument soll, nimmt der Nutzer vorher aus der Liste.
 */
export function pruefeZeilen(entwuerfe: ZeilenEntwurf[]): PruefErgebnis {
    if (entwuerfe.length === 0) return { gueltig: false, meldung: 'Keine Artikel in der Liste – bitte erst Artikel aus dem Shop holen.' };
    const zeilen: WarenkorbZeile[] = [];
    for (const z of entwuerfe) {
        const name = z.bezeichnung.trim();
        if (!name) return { gueltig: false, meldung: `Bitte für Artikel ${z.artikelnummer} eine Bezeichnung eintragen.` };
        const menge = validateDecimalInput(z.menge, { label: `die Menge bei „${name}“`, required: true });
        if (!menge.valid) return { gueltig: false, meldung: menge.message };
        if ((menge.value as number) <= 0) return { gueltig: false, meldung: `Die Menge bei „${name}“ muss größer als 0 sein.` };
        const ek = validateDecimalInput(z.einkaufspreis, { label: `den Einkaufspreis bei „${name}“`, required: true, min: 0 });
        if (!ek.valid) return { gueltig: false, meldung: ek.message };
        const aufschlag = validateDecimalInput(z.aufschlag, { label: `den Aufschlag bei „${name}“`, required: true, min: -100, max: 10000 });
        if (!aufschlag.valid) return { gueltig: false, meldung: aufschlag.message };
        zeilen.push({
            vorgangId: z.vorgangId,
            index: z.index,
            artikelnummer: z.artikelnummer,
            bezeichnung: name,
            menge: menge.value as number,
            einheit: z.einheit,
            einkaufspreis: ek.value as number,
            aufschlagProzent: aufschlag.value as number,
            artikelId: z.artikelId,
            langtext: z.langtext,
        });
    }
    return { gueltig: true, zeilen };
}

export interface WarenkorbSummen {
    einkauf: number;
    verkauf: number;
    gewinn: number;
}

/** Summen über die Zeilen: Menge × EK bzw. Menge × VK je Einheit, auf Cent gerundet. */
export function berechneSummen(zeilen: WarenkorbZeile[]): WarenkorbSummen {
    let einkauf = 0;
    let verkauf = 0;
    for (const z of zeilen) {
        einkauf += z.menge * z.einkaufspreis;
        verkauf += z.menge * verkaufspreis(z.einkaufspreis, z.aufschlagProzent);
    }
    const ek = aufCent(einkauf);
    const vk = aufCent(verkauf);
    return { einkauf: ek, verkauf: vk, gewinn: aufCent(vk - ek) };
}

/** Kurzliste für „Alles in einer Position“: „5× Silikon, 2× Mörtel“ (reiner Text, ungeschützt). */
export function sammelListeText(zeilen: WarenkorbZeile[]): string {
    return zeilen.map((z) => `${formatMenge(z.menge)}× ${z.bezeichnung}`).join(', ');
}

type IdErzeuger = () => string;

const neueId: IdErzeuger = () => crypto.randomUUID();

/**
 * Kundentext einer Einzel-Position: Bezeichnung und – falls vorhanden – der
 * Langtext des Shops (Zeilenumbrüche bleiben erhalten). Bewusst ohne
 * Artikelnummer und Lieferant: Der Kunde soll nicht sehen, wo eingekauft wird.
 */
export function kundentextEinzeln(zeile: Pick<WarenkorbZeile, 'bezeichnung' | 'langtext'>): string {
    const langtext = zeile.langtext?.trim();
    const absaetze = [`<p>${escapeHtml(zeile.bezeichnung)}</p>`];
    if (langtext) absaetze.push(`<p>${escapeHtml(langtext).replace(/\r?\n/g, '<br>')}</p>`);
    return absaetze.join('');
}

/** Je Artikel eine Position; im Kundentext steht die (ggf. umformulierte) Bezeichnung. */
export function baueEinzelBloecke(zeilen: WarenkorbZeile[], erzeugeId: IdErzeuger = neueId): DocBlock[] {
    return zeilen.map((z) => {
        const block: DocBlock = {
            id: erzeugeId(),
            type: 'SERVICE',
            title: z.bezeichnung,
            description: kundentextEinzeln(z),
            quantity: z.menge,
            unit: z.einheit,
            price: verkaufspreis(z.einkaufspreis, z.aufschlagProzent),
            fontSize: 10,
            fett: false,
            optional: false,
        };
        if (z.artikelId != null) block.artikelId = z.artikelId;
        return block;
    });
}

/** Alles in einer Pauschal-Position mit Stückliste in der Beschreibung. */
export function baueSammelBlock(zeilen: WarenkorbZeile[], ueberschrift: string, erzeugeId: IdErzeuger = neueId): DocBlock {
    return {
        id: erzeugeId(),
        type: 'SERVICE',
        title: ueberschrift.trim() || 'Material',
        description: `<p>${escapeHtml(sammelListeText(zeilen))}</p>`,
        quantity: 1,
        unit: 'pauschal',
        price: berechneSummen(zeilen).verkauf,
        fontSize: 10,
        fett: false,
        optional: false,
    };
}

/**
 * Für Live-Anzeigen (Summen, Vorschau): alle Zeilen, deren Zahlen
 * schon gültig sind. Unvollständige Zeilen zählen hier einfach (noch) nicht mit –
 * die vollständige Prüfung macht {@link pruefeZeilen} vor dem Einfügen.
 */
export function gueltigeZeilen(entwuerfe: ZeilenEntwurf[]): WarenkorbZeile[] {
    const zeilen: WarenkorbZeile[] = [];
    for (const z of entwuerfe) {
        const menge = zahlAusEntwurf(z.menge);
        const einkaufspreis = zahlAusEntwurf(z.einkaufspreis);
        const aufschlagProzent = zahlAusEntwurf(z.aufschlag);
        if (menge === null || einkaufspreis === null || aufschlagProzent === null) continue;
        zeilen.push({
            vorgangId: z.vorgangId,
            index: z.index,
            artikelnummer: z.artikelnummer,
            bezeichnung: z.bezeichnung.trim(),
            menge,
            einheit: z.einheit,
            einkaufspreis,
            aufschlagProzent,
            artikelId: z.artikelId,
            langtext: z.langtext,
        });
    }
    return zeilen;
}

/** „2 Preise aktualisiert, 1 Artikel angelegt“ – das Ergebnis des Artikelstamm-Abgleichs in einem Satz. */
export function artikelstammErgebnisText(ergebnis: { aktualisiert: number; angelegt: number }): string {
    const teile: string[] = [];
    if (ergebnis.aktualisiert > 0) teile.push(`${ergebnis.aktualisiert} ${ergebnis.aktualisiert === 1 ? 'Preis' : 'Preise'} aktualisiert`);
    if (ergebnis.angelegt > 0) teile.push(`${ergebnis.angelegt} Artikel angelegt`);
    return teile.length > 0 ? `${teile.join(', ')}.` : 'Im Artikelstamm hat sich nichts geändert.';
}

/** Was im Artikelstamm-Schritt mit einer übernommenen Zeile passieren kann. */
export type StammAktion = 'aktualisieren' | 'unveraendert' | 'neu';

/** Bekannt + gleicher Preis → nichts zu tun; bekannt + anderer Preis → aktualisieren; unbekannt → neu anlegen. */
export function stammAktionFuer(stamm: { aktuellerPreis: number | null } | null, einkaufspreis: number): StammAktion {
    if (!stamm) return 'neu';
    // Auf Millionstel genau: Kleinteile kosten oft Bruchteile eines Cents.
    if (stamm.aktuellerPreis != null && Math.abs(stamm.aktuellerPreis - einkaufspreis) < 0.000001) return 'unveraendert';
    return 'aktualisieren';
}

// ---------- Liste bearbeiten: entfernen, rückgängig, weitere Artikel ----------

/** Was {@link entferneZeile} herausgenommen hat – genug, um es an alter Stelle zurückzulegen. */
export interface EntfernteZeile<T> {
    zeile: T;
    /** Stelle in der Liste vor dem Entfernen. */
    stelle: number;
}

/** Nimmt die Zeile mit diesem Schlüssel aus der Liste; `entfernt` ist `null`, wenn es sie nicht (mehr) gibt. */
export function entferneZeile<T extends ZeilenHerkunft>(zeilen: T[], schluessel: string): { zeilen: T[]; entfernt: EntfernteZeile<T> | null } {
    const stelle = zeilen.findIndex((z) => zeilenSchluessel(z) === schluessel);
    if (stelle < 0) return { zeilen, entfernt: null };
    return { zeilen: [...zeilen.slice(0, stelle), ...zeilen.slice(stelle + 1)], entfernt: { zeile: zeilen[stelle], stelle } };
}

/**
 * Legt eine entfernte Zeile an ihre alte Stelle zurück (bzw. ans Ende, wenn die
 * Liste inzwischen kürzer ist). Ist sie schon wieder drin, bleibt alles, wie es ist.
 */
export function stelleZeileWiederHer<T extends ZeilenHerkunft>(zeilen: T[], entfernt: EntfernteZeile<T>): T[] {
    const schluessel = zeilenSchluessel(entfernt.zeile);
    if (zeilen.some((z) => zeilenSchluessel(z) === schluessel)) return zeilen;
    const stelle = Math.min(Math.max(entfernt.stelle, 0), zeilen.length);
    return [...zeilen.slice(0, stelle), entfernt.zeile, ...zeilen.slice(stelle)];
}

/**
 * Hängt die Zeilen eines weiteren Shop-Besuchs unten an. Bisherige Zeilen samt
 * Änderungen bleiben unberührt; derselbe Artikel kommt bewusst als eigene Zeile
 * dazu (nicht zusammengeführt). Zeilen eines schon übernommenen Besuchs werden
 * nicht doppelt angehängt.
 */
export function haengeZeilenAn<T extends ZeilenHerkunft>(zeilen: T[], neue: T[]): T[] {
    const vorhanden = new Set(zeilen.map(zeilenSchluessel));
    return [...zeilen, ...neue.filter((z) => !vorhanden.has(zeilenSchluessel(z)))];
}

/** „1 weiterer Artikel …“ bzw. „3 weitere Artikel aus dem Shop übernommen.“ */
export function weitereArtikelText(anzahl: number): string {
    if (anzahl === 0) return 'Im Shop-Warenkorb war diesmal nichts – die Liste bleibt, wie sie ist.';
    return anzahl === 1 ? '1 weiterer Artikel aus dem Shop übernommen.' : `${anzahl} weitere Artikel aus dem Shop übernommen.`;
}

/** Ein Artikelstamm-Auftrag für genau einen Shop-Besuch. */
export interface ArtikelstammAuftragJeBesuch {
    vorgangId: number;
    auftrag: IdsArtikelstammAuftrag;
}

/**
 * Teilt die gewählten Preis-Aktualisierungen nach Shop-Besuch auf: Der Server
 * kennt die Zeilen nur über ihren Index innerhalb des jeweiligen Besuchs.
 * Reihenfolge = erstes Auftauchen in der Liste. Nur „aktualisieren“ geht mit –
 * neue Artikel legt der Nutzer über das gewohnte Anlegen-Fenster an, deshalb
 * bleibt `neuAnlegen` immer leer.
 */
export function artikelstammAuftraegeJeBesuch(
    gewaehlt: { zeile: WarenkorbZeile; aktion: StammAktion }[],
): ArtikelstammAuftragJeBesuch[] {
    const jeBesuch = new Map<number, IdsArtikelstammAuftrag>();
    for (const { zeile, aktion } of gewaehlt) {
        if (aktion !== 'aktualisieren') continue;
        let auftrag = jeBesuch.get(zeile.vorgangId);
        if (!auftrag) {
            auftrag = { aktualisieren: [], neuAnlegen: [] };
            jeBesuch.set(zeile.vorgangId, auftrag);
        }
        auftrag.aktualisieren.push({ index: zeile.index, einkaufspreis: zeile.einkaufspreis });
    }
    return [...jeBesuch].map(([vorgangId, auftrag]) => ({ vorgangId, auftrag }));
}

/** Zählt die Ergebnisse mehrerer Artikelstamm-Aufrufe zusammen. */
export function addiereStammErgebnisse(ergebnisse: Iterable<{ aktualisiert: number; angelegt: number }>): { aktualisiert: number; angelegt: number } {
    let aktualisiert = 0;
    let angelegt = 0;
    for (const e of ergebnisse) {
        aktualisiert += e.aktualisiert;
        angelegt += e.angelegt;
    }
    return { aktualisiert, angelegt };
}

// ---------- Als Artikel anlegen (Vorbelegung fürs Anlegen-Fenster) ----------

/** Shop-Einheit → Einheit im Artikelstamm; `undefined`, wenn es keine eindeutige gibt. */
export function verrechnungseinheitAus(einheit: string): ArtikelVerrechnungseinheit | undefined {
    switch (einheit.trim().toLowerCase()) {
        case 'stk': case 'stk.': case 'stück': return 'STUECK';
        case 'm': return 'LAUFENDE_METER';
        case 'm²': case 'm2': case 'qm': return 'QUADRATMETER';
        case 'kg': return 'KILOGRAMM';
        case 'l': return 'LITER';
        default: return undefined;
    }
}

/** Hinweis oben im vorbelegten Anlegen-Fenster. */
export const SHOP_ANLEGEN_HINWEIS = 'Vom Lieferanten-Shop übernommen. Shops wie Würth schicken oft nur Kurzbezeichnungen – gib einen Namen, '
    + 'unter dem du den Artikel wiederfindest (z. B. „Neutralsilikon betongrau 310 ml“), und wähle die Kategorie.';

/**
 * Startwerte fürs Anlegen-Fenster aus einer übernommenen Zeile. Der Preis gilt
 * als „aus dem Shop“, solange er dem Shop-Preis entspricht (auf Millionstel
 * genau, wie {@link stammAktionFuer}); hat der Nutzer ihn geändert, ist es
 * seine Angabe.
 */
export function artikelVorbelegungAus(
    zeile: WarenkorbZeile,
    shopPreis: number | null,
    lieferant: { id: number; name: string },
): ArtikelVorbelegung {
    const ausDemShop = shopPreis != null && Math.abs(shopPreis - zeile.einkaufspreis) < 0.000001;
    return {
        produktname: zeile.bezeichnung,
        externeArtikelnummer: zeile.artikelnummer,
        lieferant,
        preis: zeile.einkaufspreis,
        verrechnungseinheit: verrechnungseinheitAus(zeile.einheit),
        preiseinheit: zeile.einheit,
        preisQuelle: ausDemShop ? 'IDS_SHOP' : 'MANUELL',
        hinweis: SHOP_ANLEGEN_HINWEIS,
    };
}
