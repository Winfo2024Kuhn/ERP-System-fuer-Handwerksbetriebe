import type { DocBlock } from './types';

interface AdresseKundeLike {
    name?: string;
    strasse?: string;
    plz?: string;
    ort?: string;
}

interface AnfrageAdresseLike {
    kundenName?: string;
    kundenStrasse?: string;
    kundenPlz?: string;
    kundenOrt?: string;
}

export const unitMap: Record<string, string> = {
    'LAUFENDE_METER': 'lfm',
    'QUADRATMETER': 'm²',
    'KILOGRAMM': 'kg',
    'STUECK': 'Stk'
};

/**
 * Extracts the dominant font size from HTML content.
 * Scans ALL font-size declarations (not just the first one) and returns
 * the most frequently used size (in pt, clamped to 10-20).
 *
 * WICHTIG: TiptapEditor speichert immer in pt (z.B. "12pt").
 * Falls px gefunden wird, wird es zu pt konvertiert (px * 0.75 = pt).
 */
export const extractFontSizeFromHtml = (html: string): number | undefined => {
    if (!html) return undefined;

    const fontSizeRegex = /font-size:\s*(\d+(?:\.\d+)?)(pt|px|em|rem)?/gi;
    const sizes: number[] = [];
    let match: RegExpExecArray | null;

    while ((match = fontSizeRegex.exec(html)) !== null) {
        let size = parseFloat(match[1]);
        const unit = match[2]?.toLowerCase();
        if (unit === 'px') {
            size = size * 0.75;
        } else if (unit === 'em' || unit === 'rem') {
            size = size * 10; // rough estimate: 1em ≈ 10pt base
        }
        sizes.push(Math.max(10, Math.min(20, Math.round(size))));
    }

    if (sizes.length === 0) return undefined;

    // Return the most frequently used font-size (dominant size for the block)
    const freq = new Map<number, number>();
    for (const s of sizes) {
        freq.set(s, (freq.get(s) || 0) + 1);
    }
    let dominant = sizes[0];
    let maxCount = 0;
    for (const [size, count] of freq) {
        if (count > maxCount) {
            maxCount = count;
            dominant = size;
        }
    }
    return dominant;
};

/**
 * Checks if HTML content contains bold formatting.
 */
export const extractBoldFromHtml = (html: string): boolean => {
    if (!html) return false;
    return /<(strong|b)\b/i.test(html) || /font-weight:\s*(bold|700|800|900)/i.test(html);
};

// --- Zahlungsziel-Chip: geschützter Platzhalter in Textbausteinen ---
// {{ZAHLUNGSZIEL}} (Fälligkeitsdatum) und {{ZAHLUNGSZIEL_TAGE}} (Anzahl Tage)
// werden im Editor als nicht editierbare Chips gerendert und beim Speichern
// wieder als Platzhalter serialisiert, damit die Werte nie als Klartext
// "einfrieren" und immer aus Rechnungsdatum + Zahlungsziel-Tagen folgen.

export const ZAHLUNGSZIEL_PLACEHOLDER = '{{ZAHLUNGSZIEL}}';
export const ZAHLUNGSZIEL_TAGE_PLACEHOLDER = '{{ZAHLUNGSZIEL_TAGE}}';

/**
 * Fallback-Zahlungsziel in Tagen, wenn weder Dokument noch Kunde/Anfrage eines
 * gepflegt haben. Historischer Standardwert des Dokument-Editors (§ 286 BGB
 * lässt freie Vereinbarung zu; 8 Tage waren hier schon immer der Default).
 */
export const DEFAULT_ZAHLUNGSZIEL_TAGE = 8;

/** Matcht {{ZAHLUNGSZIEL}} (case-insensitive, mit Leerraum), aber NICHT {{ZAHLUNGSZIEL_TAGE}}. */
const ZAHLUNGSZIEL_PLACEHOLDER_REGEX = /\{\{\s*ZAHLUNGSZIEL\s*\}\}/gi;

/** Matcht {{ZAHLUNGSZIEL_TAGE}} (case-insensitive, mit Leerraum). */
const ZAHLUNGSZIEL_TAGE_PLACEHOLDER_REGEX = /\{\{\s*ZAHLUNGSZIEL_TAGE\s*\}\}/gi;

/**
 * Matcht den vom Editor gerenderten Chip-Span (Attribut-Reihenfolge tolerant)
 * und fängt den Attributwert ("datum" | "tage" | legacy "true") als Gruppe 1.
 *
 * Annahme: Der Chip ist ein Tiptap-Atom-Node OHNE Kind-Elemente (siehe
 * zahlungszielChipExtension.ts) — `[\s\S]*?` bis zum ersten </span> ist daher
 * sicher. Sollte der Chip jemals verschachteltes Markup enthalten, muss diese
 * Regex auf einen echten Parser umgestellt werden.
 */
const ZAHLUNGSZIEL_CHIP_REGEX = /<span[^>]*data-zahlungsziel-chip(?:="([^"]*)")?[^>]*>[\s\S]*?<\/span>/gi;

/**
 * Ersetzt beide Zahlungsziel-Platzhalter durch Chip-Spans, die die Tiptap-
 * Extension als geschützte Atom-Nodes parst. Muss exakt das HTML erzeugen, das
 * editor.getHTML() für den Node liefert (sonst Sync-Loop im TiptapEditor).
 */
export function zahlungszielPlaceholderToChipHtml(html: string, displayDatum: string, displayTage: string): string {
    if (!html) return html;
    return html
        .replace(ZAHLUNGSZIEL_TAGE_PLACEHOLDER_REGEX, `<span data-zahlungsziel-chip="tage">${displayTage}</span>`)
        .replace(ZAHLUNGSZIEL_PLACEHOLDER_REGEX, `<span data-zahlungsziel-chip="datum">${displayDatum}</span>`);
}

/**
 * Serialisiert Chip-Spans zurück zu ihrem Platzhalter (für block.content /
 * Persistenz). Der Attributwert bestimmt den Platzhalter; legacy "true"
 * (Chips aus der ersten Chip-Version) wird als Datum interpretiert.
 */
export function chipHtmlToZahlungszielPlaceholder(html: string): string {
    if (!html) return html;
    return html.replace(ZAHLUNGSZIEL_CHIP_REGEX, (_match, variante) =>
        variante === 'tage' ? ZAHLUNGSZIEL_TAGE_PLACEHOLDER : ZAHLUNGSZIEL_PLACEHOLDER);
}

/**
 * Fälligkeitsdatum als deutsche Datums-Anzeige: Dokumentdatum + Zahlungsziel-Tage.
 * Fehlt das Dokumentdatum, wird ab heute gerechnet (analog replacePlaceholders).
 */
export function berechneZahlungszielDatum(datumIso: string | undefined, zahlungszielTage: number): string {
    const d = datumIso ? new Date(datumIso) : new Date();
    d.setDate(d.getDate() + zahlungszielTage);
    return d.toLocaleDateString('de-DE');
}

interface KontextFuerDefaults {
    kundenName?: string;
    projektnummer?: string;
    projektBauvorhaben?: string;
    kundennummer?: string;
}

interface BezugsdokumentLike {
    dokumentNummer?: string;
    datum?: string;
}

export interface BezugsdokumentKontext {
    bezugsdokument: string;
    bezugsdokumentTyp: string;
    bezugsdokumentDatum: string;
}

const BEZUGSDOKUMENTDATUM_PLACEHOLDER_REGEX = /\{\{\s*BEZUGSDOKUMENTDATUM\s*\}\}/gi;

export function ersetzeBezugsdokumentDatumPlatzhalter(html: string, datum: string): string {
    if (!html) return html;
    return html.replace(BEZUGSDOKUMENTDATUM_PLACEHOLDER_REGEX, datum);
}

export function repariereLeeresBezugsdatumInStandardtext(
    gespeicherterInhalt: string,
    templateHtml: string,
    bezugsdatum: string,
    weiterePlatzhalterAufloesen: (html: string) => string,
): string {
    if (!templateHtml || !BEZUGSDOKUMENTDATUM_PLACEHOLDER_REGEX.test(templateHtml)) {
        BEZUGSDOKUMENTDATUM_PLACEHOLDER_REGEX.lastIndex = 0;
        return gespeicherterInhalt;
    }
    BEZUGSDOKUMENTDATUM_PLACEHOLDER_REGEX.lastIndex = 0;

    const damaligerFehlerzustand = weiterePlatzhalterAufloesen(
        ersetzeBezugsdokumentDatumPlatzhalter(templateHtml, ''),
    );
    if (gespeicherterInhalt.trim() !== damaligerFehlerzustand.trim()) {
        return gespeicherterInhalt;
    }

    return weiterePlatzhalterAufloesen(
        ersetzeBezugsdokumentDatumPlatzhalter(templateHtml, bezugsdatum),
    );
}

/**
 * Standardtexte mit Bezugsplatzhaltern duerfen erst materialisiert werden,
 * wenn der asynchron geladene Vorgaenger vollstaendig im React-State steht.
 * Andernfalls wird z.B. {{BEZUGSDOKUMENTDATUM}} einmalig zu leerem Klartext
 * aufgeloest und kann durch das spaetere Kontext-Update nicht mehr repariert
 * werden.
 */
export function mussAufBezugsdokumentWarten(
    vorgaengerId: number | undefined,
    kontext: Partial<BezugsdokumentKontext>,
): boolean {
    if (!vorgaengerId) return false;
    return !kontext.bezugsdokument
        || !kontext.bezugsdokumentTyp
        || !kontext.bezugsdokumentDatum;
}

/**
 * Baut die Platzhalterwerte aus dem expliziten Vorgängerdokument.
 * ISO-Datumswerte werden ohne Date-/Timezone-Konvertierung formatiert, damit
 * das Bezugsdatum unabhängig von der Browser-Zeitzone stabil bleibt.
 */
export function buildBezugsdokumentKontext(
    vorgaenger: BezugsdokumentLike,
    typLabel: string,
): BezugsdokumentKontext {
    const datumTeile = vorgaenger.datum?.match(/^(\d{4})-(\d{2})-(\d{2})/);
    const bezugsdokumentDatum = datumTeile
        ? `${datumTeile[3]}.${datumTeile[2]}.${datumTeile[1]}`
        : '';

    return {
        bezugsdokument: vorgaenger.dokumentNummer || '',
        bezugsdokumentTyp: typLabel,
        bezugsdokumentDatum,
    };
}

/**
 * Entscheidet, ob das Laden der Standard-Textbausteine noch auf den
 * Kontext (Kunde/Projekt) warten muss.
 *
 * Gewartet wird NUR, solange der Kontext-Load laeuft (kontextGeladen=false)
 * und noch keine Kontext-Daten da sind. Ein abgeschlossener Load ohne Daten
 * (z.B. Projekt ohne Kunde/Auftragsnummer) darf die Textbausteine nicht
 * dauerhaft blockieren — das war der Bug "Angebot aus Projekt hat keine
 * Vor-/Nachtexte, aus Anfrage schon".
 */
export function mussAufKontextWarten(
    kontext: KontextFuerDefaults,
    kontextGeladen: boolean,
    hatProjektOderAnfrage: boolean,
): boolean {
    const kontextBereit = !!kontext.kundenName
        || !!kontext.projektnummer
        || !!kontext.projektBauvorhaben
        || !!kontext.kundennummer;
    return !kontextBereit && !kontextGeladen && hatProjektOderAnfrage;
}

/**
 * Entscheidet, ob vor dem Mailversand nach der Gueltigkeitsdauer des
 * Annahme-Links gefragt werden muss.
 *
 * Gefragt wird nur, wenn tatsaechlich ein Link entsteht: bei Angeboten und
 * Nachtragsangeboten, im finalen Versand (Entwuerfe bekommen nie einen Link)
 * und solange der Kunde noch nicht angenommen hat. Nach der digitalen Annahme
 * stellt das Backend bewusst keinen zweiten Link mehr aus (die Mail enthaelt
 * dann nur noch einen Hinweis auf die bestehende Annahme) — eine Frage nach
 * der Gueltigkeit waere dort irrefuehrend.
 */
export function brauchtAnnahmeLinkAbfrage(
    dokumentTyp: string,
    istEntwurfsVersand: boolean,
    digitalAngenommen: boolean | undefined,
): boolean {
    const istAngebot = dokumentTyp === 'ANGEBOT' || dokumentTyp === 'NACHTRAGSANGEBOT';
    return istAngebot && !istEntwurfsVersand && !digitalAngenommen;
}

/**
 * Dokumenttyp-Labels, unter denen die Standard-Textbausteine (Vor-/Nachtexte)
 * der Formular-Vorlage gesucht werden — in Fallback-Reihenfolge.
 * Nachtragsangebote verhalten sich wie Angebote: Ist fuer "Nachtragsangebot"
 * keine eigene Vorlage bzw. kein Standard-Text gepflegt, greifen automatisch
 * die Angebots-Defaults (gleiches Muster wie AutoMahnVersandService -> "Rechnung").
 */
export function defaultsLabelKandidaten(dokumentTyp: string, typLabel: string): string[] {
    return dokumentTyp === 'NACHTRAGSANGEBOT' ? [typLabel, 'Angebot'] : [typLabel];
}

export function buildAdresse(kunde: AdresseKundeLike | null | undefined): string {
    if (!kunde) return '';
    const parts = [];
    if (kunde.name) parts.push(kunde.name);
    if (kunde.strasse) parts.push(kunde.strasse);
    if (kunde.plz || kunde.ort) parts.push(`${kunde.plz || ''} ${kunde.ort || ''}`.trim());
    return parts.join('\n');
}

export function buildAdresseFromAnfrage(anfrage: AnfrageAdresseLike): string {
    const parts = [];
    if (anfrage.kundenName) parts.push(anfrage.kundenName);
    if (anfrage.kundenStrasse) parts.push(anfrage.kundenStrasse);
    if (anfrage.kundenPlz || anfrage.kundenOrt) parts.push(`${anfrage.kundenPlz || ''} ${anfrage.kundenOrt || ''}`.trim());
    return parts.join('\n');
}

export function blocksToHtml(blocks: DocBlock[]): string {
    return blocks.map(block => {
        if (block.type === 'TEXT') {
            return block.content || '';
        } else if (block.type === 'SEPARATOR') {
            return '<hr/>';
        } else if (block.type === 'SECTION_HEADER') {
            let html = `<h3>${block.sectionLabel || ''}</h3>`;
            if (block.children) {
                html += block.children.map(child => {
                    if (child.type === 'SERVICE') {
                        return `<div class="service-line">
                            <span class="pos">${child.pos}</span>
                            <span class="title">${child.title}</span>
                            <span class="total">${(child.quantity || 0) * (child.price || 0)}</span>
                        </div>`;
                    }
                    return '';
                }).join('\n');
            }
            return html;
        } else if (block.type === 'SERVICE') {
            return `<div class="service-line">
                <span class="pos">${block.pos}</span>
                <span class="qty">${block.quantity}</span>
                <span class="unit">${block.unit}</span>
                <span class="title">${block.title}</span>
                <span class="price">${block.price}</span>
                <span class="total">${(block.quantity || 0) * (block.price || 0)}</span>
            </div>`;
        }
        return '';
    }).join('\n');
}

/**
 * Calculates netto total from all services (root + nested in sections).
 */
/**
 * Kaufmaennisches Runden wie Javas `RoundingMode.HALF_UP`: die Haelfte geht IMMER vom
 * Nullpunkt weg. `Math.round` rundet dagegen Richtung +unendlich und weicht damit bei
 * negativen Betraegen (z.B. Gutschriften) vom Backend ab.
 */
function rundeKaufmaennisch(wert: number): number {
    return wert < 0 ? -Math.round(-wert) : Math.round(wert);
}

/**
 * Returns the line total for a single SERVICE block, applying per-position discount.
 */
/** Nachkommastellen der kuerzesten Dezimaldarstellung — wie Javas `Double.toString`. */
function dezimalstellen(wert: number): number {
    const s = String(wert);
    // Exponentialschreibweise (1e-7, 1e21) laesst sich so nicht zerlegen. Bei
    // Geldbetraegen und Mengen kommt sie nicht vor; -1 signalisiert dem Aufrufer,
    // auf die Double-Rechnung zurueckzufallen statt still 0 Stellen anzunehmen.
    if (s.includes('e') || s.includes('E')) return -1;
    const punkt = s.indexOf('.');
    return punkt < 0 ? 0 : s.length - punkt - 1;
}

/**
 * Prozentsatz in ganzzahlige Hundertstel-Prozent — exakt, ohne Double-Multiplikation.
 *
 * Entspricht `BigDecimal.valueOf(p).divide(100, 4, HALF_UP)` im Backend: der Faktor
 * hat dort vier Nachkommastellen, der Prozentsatz also effektiv zwei. `p * 100` im
 * Double verfehlt exakte Ties — `1.005 * 100` ergibt 100.49999999999999 und rundet
 * ab, wo BigDecimal aufrundet (0,0101 statt 0,0100). Bei 10.000 € sind das 1,00 €.
 */
function prozentInHundertstel(prozent: number): number {
    const skala = dezimalstellen(prozent);
    if (skala < 0) return rundeKaufmaennisch(prozent * 100);
    const mantisse = Math.round(prozent * 10 ** skala);
    return skala <= 2
        ? mantisse * 10 ** (2 - skala)
        : teileUndRunde(mantisse, 10 ** (skala - 2));
}

/** Ganzzahlige Division mit kaufmaennischer Rundung (HALF_UP, vom Nullpunkt weg). */
function teileUndRunde(zaehler: number, nenner: number): number {
    const ganz = Math.trunc(zaehler / nenner);
    const rest = Math.abs(zaehler % nenner);
    if (rest * 2 < nenner) return ganz;
    return ganz + (zaehler < 0 ? -1 : 1);
}

/**
 * `menge × preis` exakt, Ergebnis in ganzen Cent.
 *
 * Beide Faktoren werden ueber ihre Dezimalstellen ganzzahlig gemacht — genau wie
 * `BigDecimal.valueOf(double)` im Backend, das ebenfalls auf der kuerzesten
 * Dezimaldarstellung aufsetzt. Ein direktes `menge * preis` im Double verfehlt
 * exakte Halb-Cent-Werte: 4,7 × 1216,35 ergibt 5716.844999… statt 5716,845 und
 * rundet damit auf 5716,84 statt 5716,85.
 */
function produktInCent(menge: number, preis: number): number {
    const sm = dezimalstellen(menge);
    const sp = dezimalstellen(preis);
    if (sm < 0 || sp < 0) return rundeKaufmaennisch(menge * preis * 100);
    const im = Math.round(menge * 10 ** sm);
    const ip = Math.round(preis * 10 ** sp);
    const produkt = im * ip;
    if (!Number.isSafeInteger(produkt)) {
        // Praktisch unerreichbar bei Geldbetraegen; lieber ungenau als still falsch.
        return rundeKaufmaennisch(menge * preis * 100);
    }
    const skala = sm + sp;
    return skala <= 2
        ? produkt * 10 ** (2 - skala)
        : teileUndRunde(produkt, 10 ** (skala - 2));
}

 export function serviceLineTotal(b: DocBlock): number {
    // Verbindliche Zeilenregel, identisch im Backend (summeServiceBlock):
    //     round2( round2(menge × preis) × (1 − rabatt/100) )
    // Durchgehend ganzzahlig gerechnet, weil Doubles exakte Halb-Cent-Werte nicht
    // treffen. Liefen beide Seiten hier auseinander, haette der Korrekturlauf
    // korrekte, festgeschriebene Belege umgeschrieben.
    const cent = produktInCent(b.quantity || 0, b.price || 0);
    const rabatt = (b.discount && b.discount > 0) ? Math.min(100, b.discount) : 0;
    if (rabatt <= 0) return cent / 100;
    // Faktor in Zehntausendsteln — entspricht `divide(100, 4, HALF_UP)` im Backend.
    const faktorZaehler = 10000 - prozentInHundertstel(rabatt);
    return teileUndRunde(cent * faktorZaehler, 10000) / 100;
}

export function calculateNetto(blocks: DocBlock[]): number {
    let total = 0;
    for (const b of blocks) {
        if (b.type === 'SERVICE' && !b.optional) {
            total += serviceLineTotal(b);
        }
        if (b.type === 'SECTION_HEADER' && b.children) {
            for (const child of b.children) {
                if (child.type === 'SERVICE' && !child.optional) {
                    total += serviceLineTotal(child);
                }
            }
        }
    }
    return total;
}

/**
 * Normalisiert einen Rabatt-Prozentwert auf den gueltigen Bereich 0-100.
 * Unplausible Eingaben (negativ, NaN, null) ergeben 0 = kein Rabatt.
 */
export function normalisiereRabattProzent(rabattProzent?: number | null): number {
    if (rabattProzent == null || !Number.isFinite(rabattProzent) || rabattProzent <= 0) return 0;
    return Math.min(100, rabattProzent);
}

/**
 * Rabattbetrag auf zwei Nachkommastellen — genau der Wert, der im PDF als eigene
 * Rabattzeile steht.
 *
 * Pendant zu `RabattRechner#rabattBetrag` im Backend. Beide Seiten MUESSEN identisch
 * runden: weicht der gespeicherte Betrag um einen Cent vom PDF ab, haelt der
 * Korrekturlauf ein korrektes Dokument fuer falsch und schreibt eine festgeschriebene
 * Rechnung um.
 *
 * Deshalb wird in GANZZAHL-CENT gerechnet. `netto * rabatt` direkt auf dem Double
 * verfehlt jeden Halb-Cent-Fall, den die Binaerdarstellung von unten trifft:
 *   4,10 * 15  = 61.49999999999999  -> gerundet 61 statt 62  (0,61 statt 0,62)
 *   32,30 * 15 = 484.49999999999994 -> 4,84 statt 4,85
 *   2,30 * 25  =  57.49999999999999 -> 0,57 statt 0,58
 * Das Backend rechnet exakt (BigDecimal) und kaeme jeweils auf den hoeheren Wert.
 */
export function rabattBetrag(netto: number, globalRabatt?: number | null): number {
    const rabatt = normalisiereRabattProzent(globalRabatt);
    if (rabatt <= 0) return 0;
    // Backend: round2( round2(netto) × prozent / 100 ) — hier ganzzahlig nachgebildet.
    // `cent * rabatt` waere wieder eine Double-Multiplikation mit nicht-ganzzahligem
    // Faktor und wuerde bei Saetzen wie 21,4 % um einen Cent abweichen.
    const cent = rundeKaufmaennisch(netto * 100);
    const skala = dezimalstellen(rabatt);
    if (skala < 0) return rundeKaufmaennisch(cent * rabatt / 100) / 100;
    const mantisse = Math.round(rabatt * 10 ** skala);
    return teileUndRunde(cent * mantisse, 100 * 10 ** skala) / 100;
}

/** Nettobetrag nach Abzug des Dokument-Pauschalrabatts. */
export function nettoNachGlobalRabatt(netto: number, globalRabatt?: number | null): number {
    return netto - rabattBetrag(netto, globalRabatt);
}

/**
 * Nettosumme NACH Abzug des Dokument-Pauschalrabatts.
 *
 * `calculateNetto` beruecksichtigt nur die Rabatte der einzelnen Positionen
 * (`block.discount`) und laesst optionale/Alternativ-Positionen aussen vor. Der
 * Pauschalrabatt auf das gesamte Dokument liegt daneben in `globalRabatt` und muss
 * zusaetzlich abgezogen werden.
 *
 * Diese Funktion ist die massgebliche Quelle fuer den Betrag, der als `betragNetto`
 * persistiert wird. Alles was daran haengt — Projekt-Dokumentliste, Offene Posten,
 * Projekt-Bruttopreis, Rechnungs-E-Mail — rechnet sonst mit dem unrabattierten
 * Betrag weiter.
 */
export function calculateNettoNachRabatt(blocks: DocBlock[], globalRabatt?: number | null): number {
    return nettoNachGlobalRabatt(calculateNetto(blocks), globalRabatt);
}

/**
 * Gets all SERVICE blocks in document order (root + nested in sections).
 */
export function getAllServiceBlocks(blocks: DocBlock[]): DocBlock[] {
    const result: DocBlock[] = [];
    for (const b of blocks) {
        if (b.type === 'SERVICE') result.push(b);
        if (b.type === 'SECTION_HEADER' && b.children) {
            for (const child of b.children) {
                if (child.type === 'SERVICE') result.push(child);
            }
        }
    }
    return result;
}

/**
 * Liest die Bloecke aus einem positionenJson.
 *
 * Deckt beide Formate ab, die im Bestand liegen: das alte reine Block-Array und
 * das heutige `{ blocks, globalRabatt, ... }`. Kaputtes oder fehlendes JSON
 * ergibt eine leere Liste — Aufrufer zeigen dann eben keinen Positionsvergleich
 * statt den Editor mit einer Exception abzuschiessen.
 */
export function parseBlocksAusPositionenJson(json: string | null | undefined): DocBlock[] {
    if (!json) return [];
    try {
        const parsed = JSON.parse(json);
        if (Array.isArray(parsed)) return parsed as DocBlock[];
        if (parsed && Array.isArray(parsed.blocks)) return parsed.blocks as DocBlock[];
        return [];
    } catch {
        return [];
    }
}

/**
 * Vergleicht die Leistungen einer Schlussrechnung mit denen ihres Basisdokuments
 * (Angebot/Auftragsbestaetigung) und liefert die Namen der Positionen, die
 * weggefallen bzw. neu dazugekommen sind.
 *
 * Der Vergleich laeuft ueber die Block-IDs: eine Rechnung erbt die Bloecke ihres
 * Vorgaengers samt IDs, eine geloeschte Geruststellung fehlt also schlicht. Rein
 * beschreibend — der Differenzbetrag auf der Rechnung wird NICHT hieraus
 * gerechnet, sondern als Ausgleichsgroesse (Auftragssumme minus alle Rechnungen).
 * Sonst wichen Zeile und Betrag voneinander ab, sobald jemand nur eine Menge
 * aendert oder einen Rabatt setzt.
 *
 * Optionale und Alternativ-Positionen bleiben aussen vor: sie zaehlen auch nicht
 * in die Nettosumme und waeren in der Auflistung nur Rauschen.
 */
export function vergleicheLeistungen(
    basisBlocks: DocBlock[],
    dokumentBlocks: DocBlock[],
): { entfallen: string[]; zusaetzlich: string[] } {
    const namenNachId = (blocks: DocBlock[]) => {
        const map = new Map<string, string>();
        for (const b of getAllServiceBlocks(blocks)) {
            if (b.optional) continue;
            map.set(b.id, (b.title || '').trim() || 'Ohne Bezeichnung');
        }
        return map;
    };
    const basis = namenNachId(basisBlocks);
    const dokument = namenNachId(dokumentBlocks);

    const entfallen: string[] = [];
    for (const [id, name] of basis) {
        if (!dokument.has(id)) entfallen.push(name);
    }
    const zusaetzlich: string[] = [];
    for (const [id, name] of dokument) {
        if (!basis.has(id)) zusaetzlich.push(name);
    }
    return { entfallen, zusaetzlich };
}

/** Hoechstzahl namentlich genannter Leistungen in der Differenzzeile. */
const DIFFERENZ_HINWEIS_MAX = 4;

/**
 * Formatiert die Leistungsnamen fuer die Unterzeile der Differenzzeile.
 * Lange Listen werden gekuerzt, damit die Zeile auf der Rechnung nicht umbricht.
 * Leere Liste = kein Hinweis; dann steht dort nur der Betrag.
 */
export function formatiereDifferenzHinweis(namen: string[]): string {
    if (namen.length === 0) return '';
    if (namen.length <= DIFFERENZ_HINWEIS_MAX) return namen.join(', ');
    return `${namen.slice(0, DIFFERENZ_HINWEIS_MAX).join(', ')} u. a.`;
}

/**
 * Calculates the subtotal for a section's children.
 */
export function calculateSectionSubtotal(section: DocBlock): number {
    if (!section.children) return 0;
    return section.children
        .filter(c => c.type === 'SERVICE' && !c.optional)
        .reduce((sum, c) => sum + serviceLineTotal(c), 0);
}

/**
 * Flattens nested blocks (sections with children) into a flat list for PDF backend.
 * SECTION_HEADER → its children → auto-generated SUBTOTAL
 * Injects hierarchical position strings (e.g. "1.0", "1.1") from buildPositionMap.
 */
export function flattenBlocksForPdf(blocks: DocBlock[]): DocBlock[] {
    const posMap = buildPositionMap(blocks);
    const flat: DocBlock[] = [];
    for (const b of blocks) {
        if (b.type === 'SECTION_HEADER') {
            // Add section header with position (e.g. "1.0")
            flat.push({ ...b, children: undefined, pos: posMap.get(b.id) || '' });
            // Add all children with positions (e.g. "1.1", "1.2")
            if (b.children && b.children.length > 0) {
                for (const child of b.children) {
                    flat.push({ ...child, pos: posMap.get(child.id) || '' });
                }
                // Auto-generate a subtotal block
                flat.push({
                    id: `subtotal-${b.id}`,
                    type: 'SUBTOTAL',
                    sectionLabel: b.sectionLabel,
                });
            }
        } else if (b.type !== 'SUBTOTAL') {
            // Root-level blocks get their position from the map
            if (b.type === 'SERVICE') {
                flat.push({ ...b, pos: posMap.get(b.id) || '' });
            } else {
                flat.push(b);
            }
        }
    }
    return flat;
}

/**
 * Finds which container (root or section ID) a block belongs to.
 */
export function findBlockContainer(blocks: DocBlock[], itemId: string): string | null {
    for (const b of blocks) {
        if (b.id === itemId) return 'root';
        if (b.type === 'SECTION_HEADER' && b.children) {
            if (b.children.some(c => c.id === itemId)) return b.id;
        }
    }
    return null;
}

/**
 * Formats a number as German locale currency string.
 */
export function formatCurrency(value: number): string {
    return value.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

const VARIANTEN_BUCHSTABEN = 'abcdefghijklmnopqrstuvwxyz';

/**
 * Nummeriert eine Blockebene. Aufeinanderfolgende SERVICE-Bloecke derselben
 * alternativGruppe teilen sich EINE Nummer und unterscheiden sich nur im
 * Buchstaben ("2a", "2b") — im PDF ist das ohne Gruppennamen das einzige
 * Signal, welche Varianten zur selben Entscheidung gehoeren.
 */
function nummeriereEbene(
    blocks: DocBlock[],
    praefix: string,
    map: Map<string, string>,
    startCounter: number,
): void {
    let counter = startCounter;
    let i = 0;
    while (i < blocks.length) {
        const block = blocks[i];
        if (block.type !== 'SERVICE') { i++; continue; }

        const gruppe = block.alternativGruppe;
        if (!gruppe) {
            map.set(block.id, `${praefix}${counter}`);
            counter++;
            i++;
            continue;
        }

        let j = i;
        let buchstabe = 0;
        while (j < blocks.length
            && blocks[j].type === 'SERVICE'
            && blocks[j].alternativGruppe === gruppe) {
            map.set(blocks[j].id, `${praefix}${counter}${VARIANTEN_BUCHSTABEN[buchstabe] ?? ''}`);
            buchstabe++;
            j++;
        }
        counter++;
        i = j;
    }
}

/** Wie viele Positionsnummern eine Blockliste verbraucht (Gruppe = eine Nummer). */
function zaehleNummern(blocks: DocBlock[]): number {
    let anzahl = 0;
    let i = 0;
    while (i < blocks.length) {
        const block = blocks[i];
        if (block.type !== 'SERVICE') { i++; continue; }
        const gruppe = block.alternativGruppe;
        if (!gruppe) { anzahl++; i++; continue; }
        let j = i;
        while (j < blocks.length
            && blocks[j].type === 'SERVICE'
            && blocks[j].alternativGruppe === gruppe) { j++; }
        anzahl++;
        i = j;
    }
    return anzahl;
}

/**
 * Builds a position map for all blocks with hierarchical numbering.
 * SECTION_HEADER → "1.0", its children → "1.1", "1.2", etc.
 * Root-level SERVICE → next top-level number ("2", "3", etc.)
 * Varianten einer Alternativgruppe → "2a", "2b" (eine gemeinsame Nummer).
 * Other block types are skipped.
 * Returns Map<blockId, positionString>.
 */
export function buildPositionMap(blocks: DocBlock[]): Map<string, string> {
    const map = new Map<string, string>();
    let topCounter = 1;
    const rootEbene: DocBlock[] = [];

    for (const block of blocks) {
        if (block.type === 'SECTION_HEADER') {
            // Angesammelte Root-Leistungen vor der Section nummerieren.
            nummeriereEbene(rootEbene, '', map, topCounter);
            topCounter += zaehleNummern(rootEbene);
            rootEbene.length = 0;

            map.set(block.id, `${topCounter}.0`);
            nummeriereEbene(block.children ?? [], `${topCounter}.`, map, 1);
            topCounter++;
        } else if (block.type === 'SERVICE') {
            rootEbene.push(block);
        }
    }
    nummeriereEbene(rootEbene, '', map, topCounter);
    return map;
}

/** Ein Eintrag der Editor-Anzeige: einzelner Block oder eine Entweder-Oder-Gruppe. */
export type AnzeigeEintrag =
    | { art: 'block'; block: DocBlock }
    | { art: 'gruppe'; name: string; positionen: DocBlock[] };

/**
 * Bereitet eine Blockebene fuer die Anzeige auf: aufeinanderfolgende Varianten
 * derselben Gruppe werden zu einem Eintrag. Bauabschnitte bleiben stehen — fuer
 * deren children ruft die Anzeige die Funktion erneut auf.
 *
 * Bewusst spiegelbildlich zur Gruppierung der Kundenseite
 * (molecular-mercury/src/lib/freigabe-positionen.ts), damit der Handwerker im
 * Editor dieselbe Gruppierung sieht wie sein Kunde auf der Freigabe-Seite.
 */
export function gruppiereFuerAnzeige(blocks: DocBlock[]): AnzeigeEintrag[] {
    const eintraege: AnzeigeEintrag[] = [];
    let offen: { art: 'gruppe'; name: string; positionen: DocBlock[] } | null = null;

    for (const block of blocks) {
        const gruppe = block.type === 'SERVICE' && block.optional ? block.alternativGruppe : undefined;
        if (!gruppe) {
            offen = null;
            eintraege.push({ art: 'block', block });
            continue;
        }
        if (offen && offen.name === gruppe) {
            offen.positionen.push(block);
            continue;
        }
        offen = { art: 'gruppe', name: gruppe, positionen: [block] };
        eintraege.push(offen);
    }
    return eintraege;
}

export interface ClosureSectionSummary {
    label: string;
    total: number;
    position: string;
}

export interface ClosureSummary {
    sections: ClosureSectionSummary[];
    sonstigeTotal: number;
    hasSonstige: boolean;
    gesamtNetto: number;
}

/**
 * Computes the closure summary breakdown:
 * - Each Bauabschnitt with label + sum of non-optional services
 * - "Sonstige Leistungen" for root-level services not in any section
 * - Grand total
 */
export function computeClosureSummary(blocks: DocBlock[]): ClosureSummary {
    const posMap = buildPositionMap(blocks);
    const sections: ClosureSectionSummary[] = [];
    let sonstigeTotal = 0;

    for (const block of blocks) {
        if (block.type === 'CLOSURE') continue;
        if (block.type === 'SECTION_HEADER') {
            const sectionTotal = (block.children || [])
                .filter(c => c.type === 'SERVICE' && !c.optional)
                .reduce((sum, c) => sum + serviceLineTotal(c), 0);
            sections.push({
                label: block.sectionLabel || 'Bauabschnitt',
                total: sectionTotal,
                position: posMap.get(block.id) || '',
            });
        } else if (block.type === 'SERVICE' && !block.optional) {
            sonstigeTotal += serviceLineTotal(block);
        }
    }

    const gesamtNetto = sections.reduce((s, sec) => s + sec.total, 0) + sonstigeTotal;

    return {
        sections,
        sonstigeTotal,
        hasSonstige: sonstigeTotal > 0 && sections.length > 0,
        gesamtNetto,
    };
}
