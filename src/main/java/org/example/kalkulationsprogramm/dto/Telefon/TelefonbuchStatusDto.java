package org.example.kalkulationsprogramm.dto.Telefon;

import java.time.LocalDateTime;

/**
 * Stand der Übertragung ins FRITZ!Box-Telefonbuch für die Einstellungsseite.
 *
 * @param aktiv                 Schalter „Kontakte ins FRITZ!Box-Telefonbuch übertragen“
 * @param telefonAnbindungAktiv nur dann wird wirklich übertragen
 * @param telefonbuchName       Name des Telefonbuchs in der FRITZ!Box
 * @param zuletztAbgeglichen    letzter abgeschlossener Lauf oder {@code null}
 * @param anzahlEintraege       Einträge nach dem letzten Lauf
 * @param letzterFehler         Grund des letzten Fehlers in Klartext oder {@code null}
 * @param loeschungOffen        ausgeschaltet, aber das Telefonbuch ist noch nicht aus der Box gelöscht
 * @param laeuftGerade          ein Lauf ist angefordert oder läuft
 * @param letztesErgebnis       Ergebnis des letzten Laufs seit dem Serverstart oder {@code null}
 */
public record TelefonbuchStatusDto(boolean aktiv,
                                   boolean telefonAnbindungAktiv,
                                   String telefonbuchName,
                                   LocalDateTime zuletztAbgeglichen,
                                   int anzahlEintraege,
                                   String letzterFehler,
                                   boolean loeschungOffen,
                                   boolean laeuftGerade,
                                   TelefonbuchAbgleichErgebnisDto letztesErgebnis) {
}
