package org.example.kalkulationsprogramm.dto.Email;

import java.util.List;

/** Ergebnis einer Sammel-Mail, bei der jeder Empfänger eine eigene Mail bekommt. */
public record EinzelversandAntwortDto(
        int verschickt,
        List<Fehlschlag> fehlgeschlagen,
        /** Verschickt, aber im ERP nicht gespeichert – NICHT erneut senden. */
        List<String> nichtGespeichert,
        List<UnifiedEmailDto> emails) {

    public record Fehlschlag(String adresse, String grund) {
    }
}
