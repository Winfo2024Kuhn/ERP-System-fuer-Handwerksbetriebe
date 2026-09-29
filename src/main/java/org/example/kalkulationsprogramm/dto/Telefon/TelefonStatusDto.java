package org.example.kalkulationsprogramm.dto.Telefon;

import java.time.LocalDateTime;
import java.util.List;

/** Überblick für die Telefon-Seite und das Menü (nur mit Telefon-Recht). */
public record TelefonStatusDto(boolean eingerichtet,
                               List<AnrufbeantworterDto> anrufbeantworter,
                               LocalDateTime letzteAbholung,
                               String letzterFehler,
                               long neueSprachnachrichten) {
}
