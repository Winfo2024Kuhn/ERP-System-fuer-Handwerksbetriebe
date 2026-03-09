package org.example.kalkulationsprogramm.domain;

/**
 * Art eines Projekts - bestimmt ob produktive oder unproduktive Stunden.
 * Produktive Stunden werden für die Gemeinkostensatz-Berechnung verwendet.
 */
public enum ProjektArt {
    /**
     * Pauschalpreis-Projekte - produktiv.
     * Externe Kundenprojekte mit Festpreis.
     */
    PAUSCHAL(true, "Pauschalpreis"),

    /**
     * Regie-Projekte - produktiv.
     * Externe Kundenprojekte nach Aufwand.
     */
    REGIE(true, "Regie"),

    /**
     * Interne Projekte - unproduktiv.
     * Interne Arbeiten ohne Kundenabrechnung.
     */
    INTERN(false, "Internes Projekt"),

    /**
     * Garantiearbeiten - unproduktiv.
     * Nacharbeiten ohne Kundenabrechnung.
     */
    GARANTIE(false, "Garantie");

    private final boolean produktiv;
    private final String displayName;

    ProjektArt(boolean produktiv, String displayName) {
        this.produktiv = produktiv;
        this.displayName = displayName;
    }

    /**
     * @return true wenn Stunden auf diesem Projekttyp als produktiv gelten.
     */
    public boolean isProduktiv() {
        return produktiv;
    }

    /**
     * @return Anzeigename für UI.
     */
    public String getDisplayName() {
        return displayName;
    }
}
