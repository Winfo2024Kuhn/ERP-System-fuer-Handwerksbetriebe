package org.example.kalkulationsprogramm.service.telefon;

import java.time.LocalDateTime;

/**
 * Eine Nachricht auf dem Anrufbeantworter der Telefonanlage.
 *
 * @param gegenNummer  Nummer des Anrufers, "" wenn unterdrückt
 * @param eigeneNummer angerufene eigene Nummer (falls gemeldet)
 * @param downloadPfad anlagen-interner Pfad der Aufnahme (nur für {@link TelefonAnlage#ladeAudio})
 */
public record AnlagenSprachnachricht(int anrufbeantworter,
                                     LocalDateTime zeitpunkt,
                                     String gegenNummer,
                                     String eigeneNummer,
                                     String downloadPfad,
                                     String sitzung) {
}
