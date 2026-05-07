package org.example.kalkulationsprogramm.domain;

/**
 * Typen für ausgehende Geschäftsdokumente.
 *
 * <p>Die Mahn-Typen ({@link #ZAHLUNGSERINNERUNG}, {@link #ERSTE_MAHNUNG},
 * {@link #ZWEITE_MAHNUNG}) sind <strong>virtuell</strong>: Mahnungen werden
 * weiterhin als {@code ProjektGeschaeftsdokument} persistiert. Die Werte
 * existieren nur, damit die Mahn-Hierarchie (Rechnung → Zahlungserinnerung →
 * 1. Mahnung → 2. Mahnung) im Ausgangs-Dokumente-Tab des Projekt-Editors
 * sichtbar gemacht werden kann.</p>
 */
public enum AusgangsGeschaeftsDokumentTyp {
    ANGEBOT,
    AUFTRAGSBESTAETIGUNG,
    RECHNUNG,
    TEILRECHNUNG,
    ABSCHLAGSRECHNUNG,
    SCHLUSSRECHNUNG,
    GUTSCHRIFT,
    STORNO,
    ZAHLUNGSERINNERUNG,
    ERSTE_MAHNUNG,
    ZWEITE_MAHNUNG
}
