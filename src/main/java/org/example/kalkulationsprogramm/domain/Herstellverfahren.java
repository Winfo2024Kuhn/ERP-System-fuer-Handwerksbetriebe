package org.example.kalkulationsprogramm.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

/**
 * Wie der Querschnitt eines Halbzeugs entstanden ist.
 *
 * <p>Zusammen mit dem {@link Fertigungszustand} ergibt sich daraus die zutreffende
 * Massnorm. Bewusst als eigenes Feld statt nur der Norm: "EN 10219-2" sagt einem
 * Handwerker nichts, "geschweisst, kaltgefertigt" schon. In der Suche wird danach
 * gefiltert, die Norm laeuft nur als Zusatzinformation mit.
 */
@Getter
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum Herstellverfahren {

    NAHTLOS("nahtlos",
            "Aus einem Stueck gewalzt oder gezogen, ohne Laengsnaht. Gleichmaessiger im "
            + "Querschnitt und belastbarer, aber teurer als geschweisst."),

    GESCHWEISST("geschweisst",
            "Aus Band gerollt und laengs verschweisst. Der Normalfall im Metallbau - "
            + "guenstiger und fuer Gelaender, Rahmen und Konstruktionen voellig ausreichend."),

    STRANGGEPRESST("stranggepresst",
            "Der Werkstoff wird durch eine Form gepresst. Ueblich bei Aluminium, "
            + "erlaubt fast beliebige Profilquerschnitte."),

    GEWALZT("gewalzt",
            "Zwischen Walzen in Form gebracht. Ueblich bei Stabstahl, Traegern und Blechen."),

    GEZOGEN("gezogen",
            "Kalt durch ein Ziehwerkzeug gezogen. Sehr massgenau und mit glatter, "
            + "blanker Oberflaeche."),

    GEKANTET("gekantet",
            "Aus Blech abgekantet statt gewalzt. Fuer Sonderprofile, die es als "
            + "Walzprofil nicht gibt.");

    private final String anzeigename;
    private final String erklaerung;

    Herstellverfahren(String anzeigename, String erklaerung) {
        this.anzeigename = anzeigename;
        this.erklaerung = erklaerung;
    }

    public String getName() {
        return this.name();
    }

    @JsonCreator
    public static Herstellverfahren fromValue(String value) {
        for (Herstellverfahren v : values()) {
            if (v.name().equalsIgnoreCase(value)) {
                return v;
            }
        }
        throw new IllegalArgumentException("Unbekanntes Herstellverfahren: " + value);
    }
}
