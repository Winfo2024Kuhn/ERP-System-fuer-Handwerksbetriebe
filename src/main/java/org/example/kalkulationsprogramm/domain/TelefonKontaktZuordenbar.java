package org.example.kalkulationsprogramm.domain;

/**
 * Gemeinsame Sicht auf Anrufe und Sprachnachrichten, damit die Zuordnung zu
 * Kunde/Lieferant für beide mit derselben Logik läuft.
 */
public interface TelefonKontaktZuordenbar {
    String getNummerNormalisiert();

    Kunde getKunde();

    void setKunde(Kunde kunde);

    Lieferanten getLieferant();

    void setLieferant(Lieferanten lieferant);

    TelefonZuordnung getZuordnung();

    void setZuordnung(TelefonZuordnung zuordnung);
}
