package org.example.kalkulationsprogramm.repository;

/**
 * Kennzahlen eines ganzen Verlaufs, gezählt über die Mails, die der Benutzer öffnen darf.
 *
 * @param wurzelId      Wurzel des Verlaufs
 * @param anzahl        Mails im Verlauf
 * @param unzustellbar  ist irgendeine Ausgangsmail nicht angekommen?
 * @param zustellFehler Grund der neuesten nicht angekommenen Ausgangsmail, sonst {@code null}
 */
public record EmailVerlaufKennzahl(Long wurzelId, int anzahl, boolean unzustellbar, String zustellFehler) {
}
