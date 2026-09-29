package org.example.kalkulationsprogramm.domain;

/**
 * Gemeinsame Sicht auf Anrufe und Sprachnachrichten, damit die Zuordnung zu
 * Kunde/Lieferant/Steuerberater für beide mit derselben Logik läuft.
 */
public interface TelefonKontaktZuordenbar {
    String getNummerNormalisiert();

    Kunde getKunde();

    void setKunde(Kunde kunde);

    Lieferanten getLieferant();

    void setLieferant(Lieferanten lieferant);

    SteuerberaterKontakt getSteuerberater();

    void setSteuerberater(SteuerberaterKontakt steuerberater);

    TelefonZuordnung getZuordnung();

    void setZuordnung(TelefonZuordnung zuordnung);
}
