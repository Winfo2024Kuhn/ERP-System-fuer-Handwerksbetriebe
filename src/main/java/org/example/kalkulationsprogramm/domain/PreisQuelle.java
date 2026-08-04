package org.example.kalkulationsprogramm.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;

/**
 * Woher ein Lieferantenpreis stammt.
 *
 * <p>Wichtig fuer die Preishistorie: ein von Hand eingetragener Preis nach einem
 * Telefonat hat eine andere Verlaesslichkeit als ein automatisch aus einer
 * Angebots-Mail uebernommener. In der Verlaufsanzeige wird die Quelle mit
 * ausgewiesen, damit nachvollziehbar bleibt, worauf eine Kalkulation beruht.
 */
@Getter
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum PreisQuelle {

    MANUELL("Von Hand eingetragen"),
    ANGEBOT_EMAIL("Aus Angebots-Mail uebernommen"),
    CSV_IMPORT("Aus Preisliste importiert"),
    RECHNUNG("Aus einer Rechnung uebernommen"),
    SYSTEM("Vom System angelegt"),
    UNBEKANNT("Herkunft nicht erfasst");

    private final String anzeigename;

    PreisQuelle(String anzeigename) {
        this.anzeigename = anzeigename;
    }

    public String getName() {
        return this.name();
    }

    @JsonCreator
    public static PreisQuelle fromValue(String value) {
        for (PreisQuelle v : values()) {
            if (v.name().equalsIgnoreCase(value)) {
                return v;
            }
        }
        throw new IllegalArgumentException("Unbekannte Preisquelle: " + value);
    }
}
