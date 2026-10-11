package org.example.kalkulationsprogramm.dto.Benachrichtigung;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Ganze Kategorien der Glocke erledigen, z. B. alle Einträge der Spalte „Posteingänge“. */
public record BenachrichtigungKategorienErledigtRequest(
        @NotEmpty(message = "Bitte mindestens eine Kategorie angeben.")
        @Size(max = 30, message = "Höchstens 30 Kategorien auf einmal.")
        List<@NotNull @Size(max = 40) String> kategorien) {
}
