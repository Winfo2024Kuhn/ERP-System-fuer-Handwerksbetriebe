package org.example.kalkulationsprogramm.domain;

/**
 * Workflow-Zustand einer {@link ArtikelInProjekt}-Bedarfsposition.
 *
 * <pre>
 *   OFFEN ──┬──▶ AUS_LAGER   (Chef harkt ab, Preis = Ø oder manuell)
 *           ├──▶ IN_ANFRAGE  (Position liegt in Preisanfrage)
 *           └──▶ BESTELLT    (Bestellung wurde ausgeloest)
 * </pre>
 *
 * <p>Semantik: {@link ArtikelInProjekt} ist reine Bedarfs-/Kalkulationszeile,
 * die Bestellung selbst ist ein separater Aggregat-Root ({@link Bestellung}).
 * {@code quelle} sagt nur, auf welchem Weg die Zeile in die Kalkulation
 * kommt — das treibt die UI-Ansicht im Bedarf-Reiter.</p>
 */
public enum BestellQuelle {
    OFFEN,
    AUS_LAGER,
    IN_ANFRAGE,
    BESTELLT
}
