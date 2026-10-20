import type { DocBlock } from './document-editor/types';

/** Laengste erlaubte Begruendung einer Teil-Gutschrift (Vertrag: max. 200 Zeichen). */
export const GUTSCHRIFT_GRUND_MAX = 200;

/** Bezeichnung der einzigen Position einer Teil-Gutschrift. */
export function gutschriftPositionsTitel(rechnungNummer: string, grund: string): string {
    return `Gutschrift zu Rechnung ${rechnungNummer}: ${grund.trim()}`;
}

/**
 * Baut das `positionenJson` einer Teil-Gutschrift – im Format, das der
 * Dokument-Editor liest und schreibt (`{ blocks, globalRabatt }`, wie auch die
 * Teilrechnung aus der DokumentHierarchie), damit er sie ohne Umweg oeffnet.
 *
 * Genau eine Leistungsposition: Menge 1, Einheit „psch“ (pauschal), Einzelpreis
 * = Netto-Betrag. Der Betrag steht POSITIV in der Position; das Minus setzt der
 * Server beim Speichern der Gutschrift.
 *
 * @param id fester Block-Schluessel, nur fuer Tests – sonst eine neue UUID
 */
export function baueTeilGutschriftPositionen(
    rechnungNummer: string,
    grund: string,
    betragNetto: number,
    id: string = crypto.randomUUID(),
): string {
    const position: DocBlock = {
        id,
        type: 'SERVICE',
        pos: '1',
        title: gutschriftPositionsTitel(rechnungNummer, grund),
        description: '',
        quantity: 1,
        unit: 'psch',
        price: Math.abs(betragNetto),
        fontSize: 10,
        fett: false,
        optional: false,
    };
    return JSON.stringify({ blocks: [position], globalRabatt: 0 });
}

/** Heutiges Datum als `YYYY-MM-DD` in Ortszeit (nicht UTC – sonst kurz nach Mitternacht der Vortag). */
export function heuteIso(jetzt: Date = new Date()): string {
    const zwei = (n: number) => String(n).padStart(2, '0');
    return `${jetzt.getFullYear()}-${zwei(jetzt.getMonth() + 1)}-${zwei(jetzt.getDate())}`;
}
