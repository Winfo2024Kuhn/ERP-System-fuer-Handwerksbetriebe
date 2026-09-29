package org.example.kalkulationsprogramm.dto.Telefon;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Telefon-Einstellungen für die Einstellungsseite. Das Passwort wird nie
 * ausgeliefert – nur, ob eines gesetzt ist.
 */
public record TelefonEinstellungenDto(boolean aktiv,
                                      String host,
                                      String benutzer,
                                      boolean passwortGesetzt,
                                      boolean verschluesselungEingerichtet,
                                      List<String> geschaeftsnummern,
                                      List<AnrufbeantworterDto> anrufbeantworter,
                                      int aufbewahrungAnrufeMonate,
                                      int aufbewahrungSprachnachrichtenMonate,
                                      String landesvorwahl,
                                      String ortsvorwahl,
                                      LocalDateTime letzteAbholung,
                                      String letzterFehler,
                                      boolean anrufmonitorVerbunden) {
}
