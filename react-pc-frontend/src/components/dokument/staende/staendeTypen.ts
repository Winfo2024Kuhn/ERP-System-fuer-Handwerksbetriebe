/**
 * Stände eines Ausgangsdokuments („Versionskontrolle wie bei git“): Bei jedem
 * Export (Drucken, PDF speichern, E-Mail) hält das Backend den Inhalt fest.
 * Vertrag: GET /api/ausgangs-dokumente/{id}/staende und …/staende/{standId}.
 */

/** So viele Stände kommen je Seite (Backend erlaubt höchstens 50). */
export const STAENDE_SEITENGROESSE = 25;

export type StandArt = 'EXPORT' | 'VOR_WIEDERHERSTELLEN';

export interface StandEintrag {
    id: number;
    nummer: number;
    art: StandArt;
    /** ISO LocalDateTime, z. B. „2026-10-31T14:32:00“. */
    festgehaltenAm: string;
    festgehaltenVon: string | null;
    bearbeitetVon: string[];
    betragNetto: number | null;
    /** Fertige Sätze; der erste Stand hat ["Erste Fassung"]. */
    aenderungen: string[];
    wiederhergestelltAusNummer: number | null;
    /** Entspricht dem aktuellen Inhalt des Dokuments. */
    aktuell: boolean;
    pdfVorhanden: boolean;
}

export interface StaendeSeite {
    staende: StandEintrag[];
    gesamt: number;
    seite: number;
    seitenGroesse: number;
    hatMehr: boolean;
    /** Änderungen seit dem neuesten Stand; [] = nichts, null = es gibt noch keinen Stand. */
    offeneAenderungen: string[] | null;
    /** Darf das Dokument noch geändert werden? Allein danach richtet sich, ob Wiederherstellen geht. */
    dokumentBearbeitbar: boolean;
}

/** Ein einzelner Stand mit dem festgehaltenen Inhalt. */
export interface StandDetail extends StandEintrag {
    betreff: string | null;
    /** ISO-Datum des Dokuments zum Zeitpunkt des Stands. */
    datum: string | null;
    mwstSatz: number | null;
    zahlungszielTage: number | null;
    rechnungsadresseOverride: string | null;
    positionenJson: string | null;
    htmlInhalt: string | null;
    /** Darf das Dokument noch geändert werden (nicht gebucht, angenommen oder storniert)? */
    dokumentBearbeitbar: boolean;
}

/** Bezug des Dokuments; Rückfall, falls die Antwort beim Wiederherstellen Projekt oder Anfrage nicht nennt. */
export interface DokumentBezugFuerStaende {
    id?: number;
    projektId?: number;
    anfrageId?: number;
}
