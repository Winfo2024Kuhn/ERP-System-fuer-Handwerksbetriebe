package org.example.kalkulationsprogramm.dto.Email;

/** Antworten des Papierkorbs; die Feldnamen sind die JSON-Felder. */
public final class EmailPapierkorbAntwort {

    private EmailPapierkorbAntwort() {
    }

    /** {@code POST /api/emails/papierkorb} → {@code {"verschoben": n}}. */
    public record Verschoben(int verschoben) {
    }

    /** {@code POST /api/emails/papierkorb/zurueckholen} → {@code {"zurueckgeholt": n}}. */
    public record Zurueckgeholt(int zurueckgeholt) {
    }
}
