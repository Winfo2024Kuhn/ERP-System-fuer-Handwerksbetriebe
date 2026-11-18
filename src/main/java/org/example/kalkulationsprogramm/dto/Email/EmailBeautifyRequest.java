package org.example.kalkulationsprogramm.dto.Email;

import lombok.Getter;
import lombok.Setter;

/** Anfrage an {@code POST /api/email/beautify} (KI-Optimierung im E-Mail-Formular). */
@Getter
@Setter
public class EmailBeautifyRequest {
    // Eigener Text der Mail als HTML (ohne Signatur und Zitat); höchstens 500.000 Zeichen roh und 50.000 Zeichen Text ohne Tabellen/Bilder; Klartext geht auch
    private String body;
    // Optional: zitierte Vorgänger-Mail bzw. Projekt-Hinweis als Klartext, Kontext für die KI
    private String context;
    // Optional: RECHTSCHREIBUNG (Standard), FOERMLICH, LOCKER oder VERLAUF – siehe EmailSchreibstil
    private String stil;
}
