package org.example.kalkulationsprogramm.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Die 16 Bundesländer mit amtlichem Kürzel. Das Kürzel steht in den Firmendaten
 * ({@link Firmeninformation#getBundesland()}) und an jedem {@link Feiertag}.
 * Klein geschrieben ist es zugleich der Schlüssel der Feiertags-API
 * (api-feiertage.de: {@code states=by}, Feld {@code "by"}).
 */
public enum Bundesland {
    BW("Baden-Württemberg"),
    BY("Bayern"),
    BE("Berlin"),
    BB("Brandenburg"),
    HB("Bremen"),
    HH("Hamburg"),
    HE("Hessen"),
    MV("Mecklenburg-Vorpommern"),
    NI("Niedersachsen"),
    NW("Nordrhein-Westfalen"),
    RP("Rheinland-Pfalz"),
    SL("Saarland"),
    SN("Sachsen"),
    ST("Sachsen-Anhalt"),
    SH("Schleswig-Holstein"),
    TH("Thüringen");

    /** Bisheriger fester Wert; gilt für bestehende Installationen weiter. */
    public static final Bundesland STANDARD = BY;

    private final String anzeigename;

    Bundesland(String anzeigename) {
        this.anzeigename = anzeigename;
    }

    public String getAnzeigename() {
        return anzeigename;
    }

    /** Schlüssel der Feiertags-API, z. B. {@code "by"}. */
    public String apiSchluessel() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Kürzel → Bundesland; leer, wenn das Kürzel unbekannt ist. Groß-/Kleinschreibung egal. */
    public static Optional<Bundesland> ausKuerzel(String kuerzel) {
        if (kuerzel == null || kuerzel.isBlank()) {
            return Optional.empty();
        }
        String normalisiert = kuerzel.trim().toUpperCase(Locale.ROOT);
        for (Bundesland land : values()) {
            if (land.name().equals(normalisiert)) {
                return Optional.of(land);
            }
        }
        return Optional.empty();
    }
}
