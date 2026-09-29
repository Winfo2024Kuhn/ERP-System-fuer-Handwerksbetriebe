package org.example.kalkulationsprogramm.service.telefon;

/** Zugangsdaten zur Telefonanlage. {@code toString} verschweigt das Passwort. */
public record TelefonZugang(String host, String benutzer, String passwort) {
    @Override
    public String toString() {
        return "TelefonZugang[host=" + host + ", benutzer=" + benutzer + "]";
    }
}
