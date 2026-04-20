package org.example.kalkulationsprogramm.dto.Preisanfrage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * Input-DTO fuer das Erstellen einer neuen Preisanfrage an N Lieferanten.
 */
@Getter
@Setter
public class PreisanfrageErstellenDto {

    /** Optional: Projekt, zu dem die Anfrage gehoert. */
    private Long projektId;

    /** Optional: Bauvorhaben-Anzeige (falls kein Projekt verknuepft ist). */
    private String bauvorhaben;

    /** Rueckmeldefrist (Soft-Deadline). */
    private LocalDate antwortFrist;

    /** Freitext-Notiz, erscheint im PDF und in der Mail. */
    private String notiz;

    /** IDs der Lieferanten, an die die Anfrage geschickt wird. Mindestens einer. */
    private List<Long> lieferantIds = new ArrayList<>();

    /**
     * Optionale Empfaenger-Adresse pro Lieferant (Key = Lieferant-ID, Value =
     * gewaehlte E-Mail). Fehlt ein Eintrag, faellt der Versand auf die erste
     * hinterlegte E-Mail des Lieferanten zurueck. Die Adresse muss in den
     * {@code kundenEmails} des Lieferanten enthalten sein — andernfalls wird
     * die Anfrage mit {@code IllegalArgumentException} abgelehnt (kein
     * Free-Text-Versand, kein Injection-Vektor).
     */
    private Map<Long, String> empfaengerProLieferant = new HashMap<>();

    /** Positionen der Preisanfrage. Mindestens eine. */
    private List<PreisanfragePositionInputDto> positionen = new ArrayList<>();
}
