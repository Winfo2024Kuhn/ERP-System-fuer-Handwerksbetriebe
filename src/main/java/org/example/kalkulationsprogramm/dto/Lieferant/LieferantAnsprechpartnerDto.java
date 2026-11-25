package org.example.kalkulationsprogramm.dto.Lieferant;

import lombok.Getter;
import lombok.Setter;

/**
 * Ansprechpartner eines Lieferanten für den Reiter „Ansprechpartner“
 * ({@code GET/POST/PUT /api/lieferanten/{lieferantId}/ansprechpartner}).
 * Leere Felder sind {@code null}; {@code anrede} ist ein Wert von {@code Anrede} oder {@code null}.
 */
@Getter
@Setter
public class LieferantAnsprechpartnerDto {
    private Long id;
    private String anrede;
    private String vorname;
    private String nachname;
    private String aufgabe;
    private String telefon;
    private String mobiltelefon;
    private String email;
    private String notiz;
}
