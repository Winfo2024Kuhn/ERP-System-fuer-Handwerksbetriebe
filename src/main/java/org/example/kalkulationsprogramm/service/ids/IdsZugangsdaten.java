package org.example.kalkulationsprogramm.service.ids;

/**
 * Entschlüsselte Zugangsdaten für genau einen Shop-Aufruf. {@code toString} verrät das Passwort nicht.
 */
public record IdsZugangsdaten(String shopUrl, String kundennummer, String benutzername, String passwort) {

    @Override
    public String toString() {
        return "IdsZugangsdaten[shopUrl=" + shopUrl + ", kundennummer=" + kundennummer
                + ", benutzername=" + benutzername + ", passwort=***]";
    }
}
