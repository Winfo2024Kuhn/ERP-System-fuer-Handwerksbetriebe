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
}

/** Ein abgelegtes PDF. */
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
}

/** Eine Seite einer Liste. */
export interface DokumentSeite {
    eintraege: FirmenDokument[];
    gesamt: number;
    seite: number;
    seitenGroesse: number;
}

/** Angaben beim Hochladen eines PDFs. */
export interface DokumentAngaben {
    name: string;
    ordnerId: number;
    /** `yyyy-MM-dd` oder `null`. */
    faelligAm: string | null;
}

export type Faelligkeit = 'ueberfaellig' | 'bald' | 'spaeter';
