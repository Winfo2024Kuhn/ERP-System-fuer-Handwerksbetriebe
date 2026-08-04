package org.example.kalkulationsprogramm.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

/**
 * In welchem Zustand das Halbzeug geliefert wird.
 *
 * <p>Der Zustand entscheidet ueber Oberflaeche, Massgenauigkeit und Preis und ist
 * damit fuer die Kalkulation genauso wichtig wie die Abmessung. Zusammen mit dem
 * {@link Herstellverfahren} ergibt sich die zutreffende Massnorm.
 */
@Getter
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum Fertigungszustand {

    WARMGEFERTIGT("warmgefertigt",
            "Im gluehenden Zustand geformt. Runde Kanten, etwas groebere Toleranzen, "
            + "dafuer spannungsarm - gut fuer tragende Konstruktionen."),

    KALTGEFERTIGT("kaltgefertigt",
            "Bei Raumtemperatur geformt. Scharfe Kanten und engere Toleranzen - "
            + "die uebliche Wahl fuer Gelaender, Tore und Sichtkonstruktionen."),

    WARMGEWALZT("warmgewalzt",
            "Im gluehenden Zustand gewalzt. Der Standard bei Stabstahl und Traegern, "
            + "Oberflaeche mit Walzhaut."),

    KALTGEWALZT("kaltgewalzt",
            "Kalt nachgewalzt. Glattere Oberflaeche und genauere Dicke - "
            + "typisch fuer duenne Bleche."),

    KALTGEZOGEN("kaltgezogen",
            "Kalt durch ein Ziehwerkzeug gezogen. Sehr massgenau (Praezisionsrohr), "
            + "meist ohne Nacharbeit einbaufertig."),

    BLANK("blank",
            "Geschaelt oder geschliffen, metallisch blanke Oberflaeche ohne Walzhaut."),

    GEBEIZT("gebeizt",
            "Chemisch entzundert. Saubere, matte Oberflaeche - gute Grundlage "
            + "zum Beschichten."),

    GESCHLIFFEN("geschliffen",
            "Oberflaeche geschliffen (z.B. Korn 240). Bei Edelstahl im Sichtbereich "
            + "ueblich, etwa bei Gelaenderrohren.");

    private final String anzeigename;
    private final String erklaerung;

    Fertigungszustand(String anzeigename, String erklaerung) {
        this.anzeigename = anzeigename;
        this.erklaerung = erklaerung;
    }

    public String getName() {
        return this.name();
    }

    @JsonCreator
    public static Fertigungszustand fromValue(String value) {
        for (Fertigungszustand v : values()) {
            if (v.name().equalsIgnoreCase(value)) {
                return v;
            }
        }
        throw new IllegalArgumentException("Unbekannter Fertigungszustand: " + value);
    }
}
