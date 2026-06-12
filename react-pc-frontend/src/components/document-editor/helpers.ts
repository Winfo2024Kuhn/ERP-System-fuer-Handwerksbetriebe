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
 * Returns the line total for a single SERVICE block, applying per-position discount.
 */
 export function serviceLineTotal(b: DocBlock): number {
    const base = (b.quantity || 0) * (b.price || 0);
    if (b.discount && b.discount > 0) {
        return base * (1 - b.discount / 100);
    }
    return base;
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

/**
 * Builds a position map for all blocks with hierarchical numbering.
 * SECTION_HEADER → "1.0", its children → "1.1", "1.2", etc.
 * Root-level SERVICE → next top-level number ("2", "3", etc.)
 * Other block types are skipped.
 * Returns Map<blockId, positionString>.
 */
export function buildPositionMap(blocks: DocBlock[]): Map<string, string> {
    const map = new Map<string, string>();
    let topCounter = 1;

    for (const block of blocks) {
        if (block.type === 'SECTION_HEADER') {
            map.set(block.id, `${topCounter}.0`);
            if (block.children) {
                let serviceCounter = 1;
                for (const child of block.children) {
                    if (child.type === 'SERVICE') {
                        map.set(child.id, `${topCounter}.${serviceCounter}`);
                        serviceCounter++;
                    }
                }
            }
            topCounter++;
        } else if (block.type === 'SERVICE') {
            map.set(block.id, `${topCounter}`);
            topCounter++;
        }
    }
    return map;
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
