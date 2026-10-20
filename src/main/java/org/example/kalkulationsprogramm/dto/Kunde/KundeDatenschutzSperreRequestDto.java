package org.example.kalkulationsprogramm.dto.Kunde;

import jakarta.validation.constraints.Size;

/** Body von {@code POST /api/kunden/{id}/datenschutz-sperre}. */
public record KundeDatenschutzSperreRequestDto(
        @Size(max = 500, message = "Der Grund darf höchstens 500 Zeichen lang sein.") String grund) {
}
