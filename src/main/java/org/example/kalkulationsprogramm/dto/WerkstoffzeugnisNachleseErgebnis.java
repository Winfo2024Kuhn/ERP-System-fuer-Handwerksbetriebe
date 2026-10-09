package org.example.kalkulationsprogramm.dto;

import java.util.List;

/**
 * Ergebnis des einmaligen Nachlese-Laufs für Werkstoffzeugnisse.
 *
 * @param gesamt             so viele Zeugnisse hatten unvollständige Daten
 * @param erfolgreich        danach mit eigener Nummer und mindestens einer Position
 * @param fehlgeschlagen     Analyse ohne Ergebnis, mit Fehler, ohne Nummer oder ohne Positionen
 * @param verknuepft         danach mit mindestens einem anderen Dokument verknüpft (in einer Kette)
 * @param fehlgeschlageneIds IDs der fehlgeschlagenen Zeugnisse, zum Nachsehen von Hand
 */
public record WerkstoffzeugnisNachleseErgebnis(int gesamt, int erfolgreich, int fehlgeschlagen, int verknuepft,
        List<Long> fehlgeschlageneIds) {
}
