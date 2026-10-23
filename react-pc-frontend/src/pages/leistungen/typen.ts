import type { VerrechnungseinheitName } from '../../types';

/**
 * Typen der Seite „Leistungen & Kategorien“.
 *
 * Die Ordner der Leistungen SIND die Projekt-Kategorien (Produktkategorien im
 * Backend). Die Seite arbeitet intern mit Zahlen-IDs; die Rohformen der API
 * (`…Dto`) stehen daneben und werden in `ordnerLogik.ts` umgewandelt.
 */

/** Rohform aus `GET /api/produktkategorien?light=true`. */
export interface OrdnerDto {
    id: number | string;
    bezeichnung?: string | null;
    bildUrl?: string | null;
    beschreibung?: string | null;
    verrechnungseinheit?: { name: string; anzeigename?: string } | string | null;
    pfad?: string | null;
    leaf?: boolean | null;
    projektAnzahl?: number | null;
    parentId?: number | string | null;
}

/** Ein Ordner (= Projekt-Kategorie) in der Seite. */
export interface Ordner {
    id: number;
    name: string;
    parentId: number | null;
    bildUrl: string | null;
    beschreibung: string;
    einheit: VerrechnungseinheitName | null;
    /** Anzeigename der Einheit, z. B. „Laufende Meter“. */
    einheitAnzeige: string | null;
    /** Verschiedene Projekte im Ordner inkl. aller Unterordner. */
    projektAnzahl: number;
}

/** Rohform aus `GET /api/leistungen`. */
export interface LeistungDto {
    id: number | string;
    name: string;
    description?: string | null;
    price?: number | string | null;
    unit?: string | { name: string } | null;
    folderId?: number | string | null;
}

/** Eine Leistung in der Seite. */
export interface Leistung {
    id: number;
    name: string;
    beschreibung: string;
    preis: number;
    /** Einheit als Enum-Name, z. B. `LAUFENDE_METER`. */
    einheit: string;
    ordnerId: number | null;
}

/** Antwort aus `GET /api/produktkategorien/{id}/loeschpruefung`. */
export interface Loeschpruefung {
    kategorieId: number;
    bezeichnung: string;
    pfad: string | null;
    hatBezuege: boolean;
    unterordnerAnzahl: number;
    leistungAnzahl: number;
    projektAnzahl: number;
    zeitbuchungAnzahl: number;
    unterordner: { id: number; bezeichnung: string; pfad: string | null }[];
    leistungen: { id: number; name: string; ordnerId: number | null }[];
    projekte: { id: number; bauvorhaben: string | null; auftragsnummer: string | null; kunde: string | null }[];
}

export type LoeschModus = 'UMZIEHEN' | 'ALLES';

/** Antwort aus `GET /api/leistungen/{id}/verwendung`. */
export interface LeistungVerwendung {
    dokumentAnzahl: number;
    dokumente: {
        id: number;
        dokumentNummer: string | null;
        typ: string;
        datum: string | null;
        projektId: number | null;
        bauvorhaben: string | null;
    }[];
}

/** Eine Zeile aus `GET /api/produktkategorien/auswertung`. */
export interface AuswertungZeile {
    kategorieId: number;
    bezeichnung: string;
    bildUrl: string | null;
    /** Anzeigename, z. B. „Laufende Meter“. */
    verrechnungseinheit: string | null;
    leaf: boolean;
    projektAnzahl: number;
    stunden: number;
    menge: number;
    stundenProEinheit: number | null;
    leistungAnzahl: number;
}

/** Die drei Reiter rechts neben dem Baum. */
export type ReiterId = 'leistungen' | 'ordner' | 'auswertung';

/** Ein möglicher Ziel-Ordner in einer Auswahl (Verschieben, Umziehen). */
export interface ZielEintrag {
    /** `null` = oberste Ebene (kein übergeordneter Ordner). */
    id: number | null;
    name: string;
    /** Einrückung in der Liste (0 = Hauptordner). */
    ebene: number;
    /** Grund, warum das Ziel nicht geht – `null`, wenn es wählbar ist. */
    gesperrt: string | null;
}

/** Was gerade gezogen wird – während eines Ziehvorgangs innerhalb der Seite. */
export type ZiehObjekt = { art: 'ordner'; id: number } | { art: 'leistung'; id: number };
