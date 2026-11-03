/**
 * Typen der Seite „Firmendokumente“ – genau wie im API-Vertrag
 * (`docs/firmendokumente-api.md`).
 */

/** Ein Ordner aus `GET /api/firmendokumente/ordner`. Zähler gelten nur für Dokumente direkt im Ordner. */
export interface FirmenOrdner {
    id: number;
    name: string;
    parentId: number | null;
    anzahlDokumente: number;
    anzahlUeberfaellig: number;
    anzahlBaldFaellig: number;
    /** Darf der Angemeldete hier hochladen, ändern, löschen und Unterordner anlegen/umbenennen/verschieben/löschen? */
    darfAendern: boolean;
    /** `true`, wenn der oberste Ordner dieses Zweigs nur für bestimmte Abteilungen sichtbar ist (Schloss-Symbol). */
    eingeschraenkt: boolean;
}

/** Art der abgelegten Datei – entscheidet über Vorschau, Drucken und Symbol. */
export type Dateiart = 'PDF' | 'BILD' | 'SONSTIGE';

/** Ein abgelegtes Dokument (PDF, Bild oder andere Datei). */
export interface FirmenDokument {
    id: number;
    ordnerId: number;
    name: string;
    originalDateiname: string;
    groesseBytes: number;
    /** ISO LocalDateTime, z. B. `2026-11-02T09:14:00`. */
    hochgeladenAm: string;
    hochgeladenVon: string | null;
    /** `yyyy-MM-dd` oder `null`. */
    faelligAm: string | null;
    url: string;
    dateiart: Dateiart;
    /** Darf der Angemeldete das Dokument bearbeiten und löschen? */
    darfAendern: boolean;
}

/** Eine Seite einer Liste. */
export interface DokumentSeite {
    eintraege: FirmenDokument[];
    gesamt: number;
    seite: number;
    seitenGroesse: number;
}

/** Angaben beim Hochladen einer Datei. */
export interface DokumentAngaben {
    name: string;
    ordnerId: number;
    /** `yyyy-MM-dd` oder `null`. */
    faelligAm: string | null;
}

export type Faelligkeit = 'ueberfaellig' | 'bald' | 'spaeter';

/** Recht einer Abteilung an einem obersten Ordner (gilt für alle Unterordner). */
export type RechteStufe = 'KEINE' | 'ANSEHEN' | 'AENDERN';

/** Antwort von `GET/PUT /api/firmendokumente/ordner/{id}/rechte` – nur für Admins. */
export interface OrdnerRechte {
    ordnerId: number;
    ordnerName: string;
    eingeschraenkt: boolean;
    /** Alle Abteilungen, nach Name sortiert. */
    abteilungen: { abteilungId: number; name: string; stufe: RechteStufe }[];
}

/** Body von `PUT /api/firmendokumente/ordner/{id}/rechte`. */
export interface OrdnerRechteAenderung {
    eingeschraenkt: boolean;
    abteilungen: { abteilungId: number; stufe: RechteStufe }[];
}
