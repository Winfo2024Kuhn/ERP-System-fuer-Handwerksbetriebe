package org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.List;

/**
 * Antwort auf {@code POST /api/ausgangs-dokumente/{id}/storno}.
 *
 * <p>Die Felder der Stornorechnung stehen flach auf oberster Ebene (wie bisher),
 * daneben die Hinweise zu Folgen für andere Rechnungen.
 */
public record StornoAntwortDto(
        @JsonUnwrapped AusgangsGeschaeftsDokumentResponseDto storno,
        List<StornoHinweisDto> hinweise) {
}
