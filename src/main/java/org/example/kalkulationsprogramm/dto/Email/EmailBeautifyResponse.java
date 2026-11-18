package org.example.kalkulationsprogramm.dto.Email;

import lombok.Getter;
import lombok.Setter;

/** Antwort von {@code POST /api/email/beautify}. */
@Getter
@Setter
public class EmailBeautifyResponse {
    // Bereinigtes HTML des neu formulierten Texts; Tabellen und Bilder der Eingabe unverändert
    private String body;
}
