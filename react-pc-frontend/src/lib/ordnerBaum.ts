/**
 * Reine Baum-Logik für Ordner-Ablagen (Leistungen & Kategorien, Firmendokumente …):
 * Baum aufbauen, Kinder, Nachfahren, Pfade und sichtbare Zeilen für die
 * Tastatur-Bedienung. Ohne React und ohne Netzwerk – deshalb vollständig per
 * Vitest geprüft. Fachliche Regeln einer Seite bleiben in deren eigener Logik.
 */

/** Das Mindeste, was ein Ordner für den Baum mitbringen muss. */
export interface BaumOrdner {
    id: number;
    parentId: number | null;
    name: string;
}

export interface Baum<T extends BaumOrdner = BaumOrdner> {
    byId: Map<number, T>;
    /** Kinder je Eltern-ID (`null` = Hauptordner), nach Name sortiert. */
    kinder: Map<number | null, T[]>;
}

/** Sortierung nach Name ohne Groß/Klein, wie im deutschen Alphabet. */
export const nachName = (a: { name: string }, b: { name: string }) => a.name.localeCompare(b.name, 'de', { sensitivity: 'base' });

export function baueBaum<T extends BaumOrdner>(ordner: readonly T[]): Baum<T> {
    const byId = new Map(ordner.map((o) => [o.id, o]));
    const kinder = new Map<number | null, T[]>();
    for (const o of ordner) {
        // Unbekannter Eltern-Ordner (z. B. gerade ausgeblendet): als Hauptordner zeigen statt verschwinden lassen.
        const eltern = o.parentId !== null && byId.has(o.parentId) ? o.parentId : null;
        const liste = kinder.get(eltern) ?? [];
        liste.push(o);
        kinder.set(eltern, liste);
    }
    for (const liste of kinder.values()) liste.sort(nachName);
    return { byId, kinder };
}

export function kinderVon<T extends BaumOrdner>(baum: Baum<T>, id: number | null): T[] {
    return baum.kinder.get(id) ?? [];
}

export function hatUnterordner<T extends BaumOrdner>(baum: Baum<T>, id: number): boolean {
    return kinderVon(baum, id).length > 0;
}

/** Alle Unterordner (beliebig tief), ohne den Ordner selbst. */
export function nachfahren<T extends BaumOrdner>(baum: Baum<T>, id: number): Set<number> {
    const ergebnis = new Set<number>();
    const stapel = [id];
    while (stapel.length) {
        const aktuell = stapel.pop() as number;
        for (const kind of kinderVon(baum, aktuell)) {
            if (kind.id === id || ergebnis.has(kind.id)) continue;
            ergebnis.add(kind.id);
            stapel.push(kind.id);
        }
    }
    return ergebnis;
}

/** Kette vom Hauptordner bis zum Ordner selbst. Sicher gegen kaputte (zyklische) Daten. */
export function ordnerKette<T extends BaumOrdner>(baum: Baum<T>, id: number): T[] {
    const kette: T[] = [];
    const gesehen = new Set<number>();
    let aktuell = baum.byId.get(id);
    while (aktuell && !gesehen.has(aktuell.id)) {
        gesehen.add(aktuell.id);
        kette.unshift(aktuell);
        aktuell = aktuell.parentId === null ? undefined : baum.byId.get(aktuell.parentId);
    }
    return kette;
}

/** Pfad als Text, z. B. „Prüfungen › Tore“. */
export function pfadText<T extends BaumOrdner>(baum: Baum<T>, id: number, trenner = ' › '): string {
    return ordnerKette(baum, id).map((o) => o.name).join(trenner);
}

export interface BaumZeile<T extends BaumOrdner = BaumOrdner> {
    ordner: T;
    ebene: number;
    hatKinder: boolean;
    aufgeklappt: boolean;
}

/** Zeilen in Anzeige-Reihenfolge: nur, was durch aufgeklappte Ordner sichtbar ist (optional gefiltert). */
export function sichtbareZeilen<T extends BaumOrdner>(baum: Baum<T>, aufgeklappt: ReadonlySet<number>, filter?: ReadonlySet<number> | null): BaumZeile<T>[] {
    const zeilen: BaumZeile<T>[] = [];
    const gesehen = new Set<number>();
    const besuche = (elternId: number | null, ebene: number) => {
        for (const o of kinderVon(baum, elternId)) {
            if (gesehen.has(o.id) || (filter && !filter.has(o.id))) continue;
            gesehen.add(o.id);
            const kinder = kinderVon(baum, o.id).filter((k) => !filter || filter.has(k.id));
            const offen = kinder.length > 0 && aufgeklappt.has(o.id);
            zeilen.push({ ordner: o, ebene, hatKinder: kinder.length > 0, aufgeklappt: offen });
            if (offen) besuche(o.id, ebene + 1);
        }
    };
    besuche(null, 0);
    return zeilen;
}

/** Alle Ordner in Baum-Reihenfolge, je mit Einrückung (alles aufgeklappt). */
export function alleInReihenfolge<T extends BaumOrdner>(baum: Baum<T>): { ordner: T; ebene: number }[] {
    const alle = new Set(baum.byId.keys());
    return sichtbareZeilen(baum, alle).map((z) => ({ ordner: z.ordner, ebene: z.ebene }));
}

/** Enthält `text` den Suchtext (ohne Groß/Klein, Leerzeichen am Rand egal)? */
export function passtZurSuche(text: string, suchtext: string): boolean {
    return text.toLocaleLowerCase('de').includes(suchtext.trim().toLocaleLowerCase('de'));
}

/** Ziel „oberste Ebene“ in der Auswahl beim Verschieben eines Ordners. */
export const OBERSTE_EBENE = 'Oberste Ebene (kein übergeordneter Ordner)';

/** Ein Eintrag in der Ziel-Auswahl beim Verschieben. */
export interface ZielEintrag {
    /** `null` = oberste Ebene (kein übergeordneter Ordner). */
    id: number | null;
    name: string;
    /** Einrückung in der Liste (0 = Hauptordner). */
    ebene: number;
    /** Grund, warum das Ziel nicht geht – `null`, wenn es wählbar ist. */
    gesperrt: string | null;
}
