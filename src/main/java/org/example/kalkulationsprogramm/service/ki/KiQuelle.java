package org.example.kalkulationsprogramm.service.ki;

/** Eine Internet-Seite, auf die sich eine Antwort mit Websuche stützt. */
public record KiQuelle(String titel, String adresse) {

    public KiQuelle {
        adresse = adresse == null ? "" : adresse;
        titel = titel == null || titel.isBlank() ? adresse : titel;
    }
}
