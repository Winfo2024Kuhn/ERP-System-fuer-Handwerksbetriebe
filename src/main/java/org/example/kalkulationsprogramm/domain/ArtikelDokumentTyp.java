package org.example.kalkulationsprogramm.domain;

/**
 * Dokumenttypen fuer Artikel-Anhaenge.
 * VORSCHAUBILD ist kein eigenes Feld am Artikel, sondern ein Dokument mit
 * diesem besonderen Typ - hoechstens eines je Artikel, durchgesetzt vom
 * Service (kein DB-Constraint).
 */
public enum ArtikelDokumentTyp {
    VORSCHAUBILD,
    ZULASSUNG,
    ZEICHNUNG,
    DATENBLATT,
    MONTAGEANLEITUNG,
    SONSTIGES
}
