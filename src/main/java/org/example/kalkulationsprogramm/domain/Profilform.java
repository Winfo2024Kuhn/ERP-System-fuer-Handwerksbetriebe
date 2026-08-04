package org.example.kalkulationsprogramm.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

/**
 * Geometrische Grundform eines Halbzeugs.
 *
 * <p>Bewusst als eigenes Feld und nicht nur ueber die Kategorie abgebildet: Kategorien
 * duerfen vom Anwender umbenannt und umsortiert werden, die Formel zur Gewichts- und
 * Oberflaechenberechnung muss aber stabil an der Form haengen bleiben.
 *
 * <p>{@link #istHohlprofil()} und {@link #istBlech()} steuern, welche Masse ueberhaupt
 * sinnvoll sind - ein Rundstab hat keine Wandstaerke, ein Blech kein Gewicht je Meter.
 */
@Getter
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum Profilform {

    RUNDROHR("Rundrohr", "Rundes Hohlprofil, z.B. Handlauf 42,4 x 2"),
    QUADRATROHR("Quadratrohr", "Quadratisches Hohlprofil, z.B. Pfosten 40 x 40 x 2"),
    RECHTECKROHR("Rechteckrohr", "Rechteckiges Hohlprofil, z.B. Riegel 60 x 40 x 3"),

    RUNDSTAB("Rundstab", "Voller runder Stab, z.B. Fuellstab 12 mm"),
    VIERKANTSTAB("Vierkantstab", "Voller quadratischer Stab, z.B. 12 x 12"),
    SECHSKANTSTAB("Sechskantstab", "Voller sechseckiger Stab, meist fuer Drehteile"),
    FLACHSTAB("Flachstahl", "Flaches Vollmaterial, z.B. 40 x 8"),
    BREITFLACHSTAHL("Breitflachstahl", "Flachmaterial ab 150 mm Breite"),

    WINKEL_GLEICHSCHENKLIG("Winkel gleichschenklig", "L-Profil mit gleich langen Schenkeln, z.B. 40 x 40 x 4"),
    WINKEL_UNGLEICHSCHENKLIG("Winkel ungleichschenklig", "L-Profil mit unterschiedlichen Schenkeln, z.B. 60 x 40 x 5"),

    U_PROFIL("U-Profil", "U-Form mit geneigten Flanschen (DIN 1026-1)"),
    UPE_PROFIL("UPE-Profil", "U-Form mit parallelen Flanschen (DIN 1026-2)"),
    UAP_PROFIL("UAP-Profil", "U-Form mit parallelen Flanschen, franzoesische Reihe"),

    I_PROFIL("I-Profil", "Doppel-T-Traeger, klassische Reihe"),
    IPE_PROFIL("IPE-Traeger", "Schmaler Doppel-T-Traeger, der Standardtraeger"),
    HEA_PROFIL("HEA-Traeger", "Breiter Traeger, leichte Ausfuehrung"),
    HEB_PROFIL("HEB-Traeger", "Breiter Traeger, schwere Ausfuehrung"),
    HEM_PROFIL("HEM-Traeger", "Breiter Traeger, verstaerkte Ausfuehrung"),

    T_PROFIL("T-Profil", "T-foermiges Profil"),
    Z_PROFIL("Z-Profil", "Z-foermiges Profil"),

    BLECH("Blech", "Glattes Blech, wird nach Quadratmeter gerechnet"),
    RIFFELBLECH("Riffelblech", "Blech mit erhabenem Muster, rutschhemmend - fuer Stufen und Podeste"),
    LOCHBLECH("Lochblech", "Blech mit Lochung, z.B. fuer Sichtschutz und Verkleidungen"),

    SONSTIGES("Sonstiges", "Form ohne eigene Berechnungsformel");

    private final String anzeigename;
    private final String erklaerung;

    Profilform(String anzeigename, String erklaerung) {
        this.anzeigename = anzeigename;
        this.erklaerung = erklaerung;
    }

    public String getName() {
        return this.name();
    }

    /** Hohlprofile haben eine Wandstaerke und werden nach Aussenmass minus Wand gerechnet. */
    public boolean istHohlprofil() {
        return this == RUNDROHR || this == QUADRATROHR || this == RECHTECKROHR;
    }

    /** Bleche werden nach Quadratmeter abgerechnet, nicht nach laufendem Meter. */
    public boolean istBlech() {
        return this == BLECH || this == RIFFELBLECH || this == LOCHBLECH;
    }

    /** Rundformen werden ueber den Durchmesser beschrieben, nicht ueber Hoehe und Breite. */
    public boolean istRund() {
        return this == RUNDROHR || this == RUNDSTAB;
    }

    @JsonCreator
    public static Profilform fromValue(String value) {
        for (Profilform v : values()) {
            if (v.name().equalsIgnoreCase(value)) {
                return v;
            }
        }
        throw new IllegalArgumentException("Unbekannte Profilform: " + value);
    }
}
