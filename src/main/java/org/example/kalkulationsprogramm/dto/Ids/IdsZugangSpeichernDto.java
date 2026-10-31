package org.example.kalkulationsprogramm.dto.Ids;

import org.example.kalkulationsprogramm.domain.ids.IdsProtokoll;

/**
 * Shop-Zugang anlegen oder ändern. {@code passwort} leer oder {@code null} = bisheriges Passwort bleibt.
 * {@code toString} verrät das Passwort nicht.
 */
public record IdsZugangSpeichernDto(boolean aktiv, IdsProtokoll protokoll, String shopUrl, String kundennummer,
        String benutzername, String passwort) {

    @Override
    public String toString() {
        return "IdsZugangSpeichernDto[aktiv=" + aktiv + ", protokoll=" + protokoll + ", shopUrl=" + shopUrl + "]";
    }
}
