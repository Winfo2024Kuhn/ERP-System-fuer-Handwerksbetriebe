package org.example.kalkulationsprogramm.domain;

/**
 * Kategorie eines Projekt-Bautagebuch-Eintrags.
 * <p>
 * Wird vom EN-1090-Akte-Export ausgewertet, um relevante Einträge
 * (Verbindungsmittel, Schweißnähte, Korrosionsschutz) automatisch
 * pro Projekt einzusammeln. Foto + Notiz reichen als Audit-Nachweis,
 * weil ETA-Nummer und Charge auf dem Etikett stehen.
 */
public enum ProjektNotizKategorie {
    ALLGEMEIN,
    VERBINDUNGSMITTEL,
    SCHWEISSUNG,
    KORROSIONSSCHUTZ,
    OBERFLAECHE;

    /**
     * Robustes Parsen einer Kategorie aus einem String. Null, Leerstring
     * und unbekannte Werte fallen auf {@link #ALLGEMEIN} zurück, damit ältere
     * Mobile-Clients ohne Kategorie-Feld weiter funktionieren. Großschreibung
     * wird normalisiert. Unbekannte Werte sind absichtlich tolerant – ein
     * Mobile-Bug darf nicht zu HTTP 400 beim Bautagebuch-Speichern führen.
     */
    public static ProjektNotizKategorie fromStringOrDefault(String value) {
        if (value == null || value.isBlank()) {
            return ALLGEMEIN;
        }
        try {
            return ProjektNotizKategorie.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ALLGEMEIN;
        }
    }
}
