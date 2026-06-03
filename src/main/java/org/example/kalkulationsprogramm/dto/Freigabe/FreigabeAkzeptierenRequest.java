package org.example.kalkulationsprogramm.dto.Freigabe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Request-Body für POST /api/internal/freigabe/{uuid}/akzeptieren.
 *
 * <p>Vor- und Nachname sind seit der Beweissicherungs-Erweiterung Pflicht – bei
 * Firmenkunden ist das die vertretungsberechtigte Person. Die Bean-Validation
 * sorgt für HTTP 400 mit Feldname (siehe {@code RestExceptionHandler}), wenn
 * Astro nicht beide Felder mitschickt.</p>
 */
@Getter
@Setter
public class FreigabeAkzeptierenRequest
{
    /** Vom Kunden bestätigte E-Mail (optional, kommt aus dem Akzeptanz-Formular). */
    private String email;

    /** Vorname der freigebenden Person bzw. der vertretungsberechtigten Person. */
    @NotBlank(message = "Vorname ist erforderlich.")
    @Size(min = 2, max = 80, message = "Vorname muss zwischen 2 und 80 Zeichen lang sein.")
    private String vorname;

    /** Nachname der freigebenden Person bzw. der vertretungsberechtigten Person. */
    @NotBlank(message = "Nachname ist erforderlich.")
    @Size(min = 2, max = 80, message = "Nachname muss zwischen 2 und 80 Zeichen lang sein.")
    private String nachname;

    /**
     * Zusammengesetzter Anzeigename "Vorname Nachname" – wird von Astro vorberechnet
     * mitgesendet, damit serverseitig kein Format-Mismatch entsteht.
     */
    @NotBlank(message = "Unterzeichnername ist erforderlich.")
    @Size(min = 2, max = 160, message = "Unterzeichnername muss zwischen 2 und 160 Zeichen lang sein.")
    private String unterzeichnerName;

    /** Pflicht-Bestätigung "Ich habe das Dokument geprüft und nehme es verbindlich an". */
    private boolean bestaetigung;
    /** Vom Astro-Layer aus Request-Headern extrahierte echte Client-IP. */
    private String clientIp;
    /** Vom Astro-Layer extrahierter User-Agent. */
    private String userAgent;

    /**
     * blockIds der optionalen (alternativen) Positionen, die der Kunde mitbeauftragt.
     * Leer/null = keine Alternativen gewählt. Der Server validiert jede ID gegen die
     * tatsächlich optionalen Positionen des Dokuments und berechnet den verbindlichen
     * Betrag selbst neu – der Client-Betrag wird nie vertraut.
     */
    private List<String> ausgewaehlteAlternativen;
}
