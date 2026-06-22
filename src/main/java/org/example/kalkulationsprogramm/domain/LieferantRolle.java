package org.example.kalkulationsprogramm.domain;

/**
 * Rolle(n), die ein Lieferant im Stahl-/Metallbau einnehmen kann.
 * Ein Lieferant kann mehrere Rollen haben (1:n, siehe {@link Lieferanten#getRollen()}).
 * Kategorien hinterlegen "typische Rollen" ({@link Kategorie#getTypischeRollen()}), damit
 * beim Preis-Eintragen am Artikel die passenden Lieferanten vorgeschlagen werden.
 *
 * Loest den alten freien {@code lieferantenTyp}-String ab. Werte exakt = Spaltenwerte
 * der nativen MySQL-ENUM-Spalte (siehe V330-Migration).
 */
public enum LieferantRolle {
    STAHLHANDEL("Stahlhandel"),
    SCHRAUBEN_NORMTEILE("Schrauben & Normteile"),
    BESCHICHTUNG_VERZINKEN("Beschichtung / Verzinkerei"),
    LACKIERER("Lackierer"),
    FERTIGTEILE_ZUKAUF("Fertigteile & Zukauf"),
    ALUMINIUM_NE("Aluminium / NE-Metalle"),
    EDELSTAHL("Edelstahl"),
    WERKZEUG_VERBRAUCH("Werkzeug & Verbrauch"),
    IT("IT"),
    SONSTIGER("Sonstiger / Dienstleister");

    private final String anzeigename;

    LieferantRolle(String anzeigename) {
        this.anzeigename = anzeigename;
    }

    public String getAnzeigename() {
        return anzeigename;
    }
}
