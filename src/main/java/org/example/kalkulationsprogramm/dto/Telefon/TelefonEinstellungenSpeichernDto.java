package org.example.kalkulationsprogramm.dto.Telefon;

import java.util.List;

/**
 * Änderung der Telefon-Einstellungen. {@code passwort == null} oder leer lässt
 * das gespeicherte Passwort unverändert.
 */
public record TelefonEinstellungenSpeichernDto(Boolean aktiv,
                                               String host,
                                               String benutzer,
                                               String passwort,
                                               List<String> geschaeftsnummern,
                                               List<AnrufbeantworterDto> anrufbeantworter,
                                               Integer aufbewahrungAnrufeMonate,
                                               Integer aufbewahrungSprachnachrichtenMonate) {
}
