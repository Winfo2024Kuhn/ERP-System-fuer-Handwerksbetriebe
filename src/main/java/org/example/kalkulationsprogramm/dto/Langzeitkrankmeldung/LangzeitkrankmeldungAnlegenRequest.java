package org.example.kalkulationsprogramm.dto.Langzeitkrankmeldung;

import lombok.Data;

import java.time.LocalDate;

/**
 * Request-Body fuer {@code POST /api/langzeitkrankmeldungen} (Anlegen) und
 * {@code PUT /api/langzeitkrankmeldungen/{id}} (Aendern, dort bleibt
 * {@code mitarbeiterId} unbeachtet, die ID kommt aus dem Pfad).
 */
@Data
public class LangzeitkrankmeldungAnlegenRequest {
    private Long mitarbeiterId;
    private LocalDate beginn;
    /** Optional: ueberschreibt den Default (Beginn + 41 Tage). */
    private LocalDate lohnfortzahlungBis;
    private String notiz;
}
