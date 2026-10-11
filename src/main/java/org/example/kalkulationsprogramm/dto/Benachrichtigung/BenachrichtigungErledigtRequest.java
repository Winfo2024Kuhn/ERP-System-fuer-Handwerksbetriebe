package org.example.kalkulationsprogramm.dto.Benachrichtigung;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Einträge der Glocke, die der Benutzer erledigt hat (ids aus {@link BenachrichtigungEintragDto#id()}). */
public record BenachrichtigungErledigtRequest(
        @NotEmpty(message = "Bitte mindestens einen Eintrag angeben.")
        @Size(max = 200, message = "Höchstens 200 Einträge auf einmal.")
        List<@NotNull @Size(max = 120) @Pattern(regexp = "[A-Z_]{1,40}+:[A-Za-z0-9_.-]{1,79}+",
                message = "Unbekannter Eintrag.") String> ids) {
}
