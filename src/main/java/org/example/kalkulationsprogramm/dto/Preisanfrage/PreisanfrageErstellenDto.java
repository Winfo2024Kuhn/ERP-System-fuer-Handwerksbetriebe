package org.example.kalkulationsprogramm.dto.Preisanfrage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    /** Positionen der Preisanfrage. Mindestens eine. */
    private List<PreisanfragePositionInputDto> positionen = new ArrayList<>();
}
