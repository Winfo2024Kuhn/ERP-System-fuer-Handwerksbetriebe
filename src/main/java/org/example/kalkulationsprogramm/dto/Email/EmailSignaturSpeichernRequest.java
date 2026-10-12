package org.example.kalkulationsprogramm.dto.Email;

/**
 * Anlegen oder Ändern einer Signatur.
 *
 * @param id           {@code null} = neue Signatur
 * @param vorlageDaten Formularzustand des Signatur-Studios (JSON) oder {@code null}
 * @param besitzerId        nur für Admins: Besitzer, {@code null} = ohne Besitzer. Bei Benutzern ignoriert.
 * @param besitzerFestlegen nur für Admins: {@code true} = {@code besitzerId} übernehmen. Fehlt das Flag,
 *                          bleibt der Besitzer unverändert (neue Signaturen: der Admin selbst) – so löscht
 *                          ein Request ohne {@code besitzerId} den Besitzer nicht versehentlich.
 * @param alsStandard       {@code true} = danach Standard-Signatur des angemeldeten Benutzers
 * @param firmenVorlageId   Firmen-Vorlage, aus der die Signatur gebaut wurde; {@code null} = freies Design.
 *                          Neue Signaturen von Benutzern brauchen eine, sobald es Firmen-Vorlagen gibt.
 */
public record EmailSignaturSpeichernRequest(
        Long id,
        String name,
        String html,
        String vorlageDaten,
        Long besitzerId,
        Boolean besitzerFestlegen,
        Boolean alsStandard,
        Long firmenVorlageId) {
}
